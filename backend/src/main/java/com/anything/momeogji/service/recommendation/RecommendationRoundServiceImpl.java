package com.anything.momeogji.service.recommendation;

import com.anything.momeogji.dto.recommendation.PersonalOptionRequest;
import com.anything.momeogji.dto.recommendation.RecommendationRequest;
import com.anything.momeogji.dto.recommendation.RecommendationResult;
import com.anything.momeogji.dto.recommendation.RestaurantRecommendation;
import com.anything.momeogji.dto.recommendation.RoundCreateRequest;
import com.anything.momeogji.dto.recommendation.RoundResponse;
import com.anything.momeogji.entity.Member;
import com.anything.momeogji.entity.recommendation.Meetup;
import com.anything.momeogji.entity.recommendation.MeetupParticipant;
import com.anything.momeogji.entity.recommendation.RecommendationRound;
import com.anything.momeogji.entity.recommendation.RecommendationRoundStatus;
import com.anything.momeogji.entity.recommendation.Restaurant;
import com.anything.momeogji.entity.recommendation.RoundCandidate;
import com.anything.momeogji.entity.recommendation.SubmissionStatus;
import com.anything.momeogji.repository.ChatRoomMemberRepository;
import com.anything.momeogji.repository.MeetupParticipantRepository;
import com.anything.momeogji.repository.MeetupRepository;
import com.anything.momeogji.repository.MemberRepository;
import com.anything.momeogji.repository.RecommendationRoundRepository;
import com.anything.momeogji.repository.RestaurantRepository;
import com.anything.momeogji.repository.RoundCandidateRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
@RequiredArgsConstructor
public class RecommendationRoundServiceImpl implements RecommendationRoundService {

    /** 네 번째 선택지를 일반 식당과 같은 후보/투표 구조로 다루기 위한 내부 장소 ID. */
    static final String RECOMMEND_AGAIN_PLACE_ID = "__RECOMMEND_AGAIN__";

    private final MeetupRepository meetupRepository;
    private final ChatRoomMemberRepository chatRoomMemberRepository;
    private final MeetupParticipantRepository meetupParticipantRepository;
    private final MemberRepository memberRepository;
    private final ParticipantPreferenceUpserter participantPreferenceUpserter;
    private final RecommendationRoundRepository recommendationRoundRepository;
    private final RoundCandidateRepository roundCandidateRepository;
    private final RestaurantRepository restaurantRepository;
    private final RestaurantRecommendationService restaurantRecommendationService;
    private final RecommendationEventPublisher eventPublisher;
    private final RoundResponseAssembler roundResponseAssembler;

    @Override
    @Transactional
    public RoundResponse createRound(Long meetupId, RoundCreateRequest request, Long callerId) {
        Meetup meetup = findMeetup(meetupId);
        requireMembership(meetup, callerId);
        persistPreferences(meetup, request.personalOptions());
        return executeRecommendation(meetup, request.personalOptions(), request.preferenceNote());
    }

    @Override
    @Transactional
    public RoundResponse triggerAutoRecommendation(Meetup meetup, List<PersonalOptionRequest> personalOptions) {
        return executeRecommendation(meetup, personalOptions, null);
    }

    private RoundResponse executeRecommendation(Meetup meetup, List<PersonalOptionRequest> personalOptions, String preferenceNote) {
        Long chatRoomId = meetup.getChatRoom().getId();
        List<String> excludedRestaurantIds = derivePreviouslyRecommendedRestaurantIds(meetup.getId());
        RecommendationRequest recommendationRequest = new RecommendationRequest(
                MeetupCommonOptionMapper.toCommonOption(meetup),
                personalOptions,
                excludedRestaurantIds,
                preferenceNote
        );

        eventPublisher.recommendationStarted(chatRoomId);
        RecommendationResult result;
        try {
            result = restaurantRecommendationService.recommend(recommendationRequest);
        } catch (RuntimeException e) {
            eventPublisher.recommendationFailed(chatRoomId, e.getMessage());
            throw e;
        }

        RecommendationRound round = persistRound(meetup, preferenceNote, result);
        meetup.markVoting();

        RoundResponse response = roundResponseAssembler.assemble(round);
        eventPublisher.recommendationCompleted(chatRoomId, response);
        return response;
    }

    private RecommendationRound persistRound(Meetup meetup, String preferenceNote, RecommendationResult result) {
        int nextRoundNo = (int) recommendationRoundRepository.countByMeetupId(meetup.getId()) + 1;
        RecommendationRound round = recommendationRoundRepository.save(RecommendationRound.builder()
                .meetup(meetup)
                .roundNo(nextRoundNo)
                .status(RecommendationRoundStatus.COMPLETED)
                .preferenceNote(preferenceNote)
                .participantCount(result.participantCount())
                .build());

        for (RestaurantRecommendation recommendation : result.recommendations()) {
            Restaurant restaurant = upsertRestaurant(recommendation);
            roundCandidateRepository.save(RoundCandidate.builder()
                    .round(round)
                    .restaurant(restaurant)
                    .rankNo(recommendation.rank())
                    .reason(recommendation.reason())
                    .imageUrl(recommendation.imageUrl())
                    .build());
        }

        // 재추천도 후보로 저장해야 복수 선택, 투표 변경, 실시간 집계가 동일한 API로 동작한다.
        Restaurant recommendAgain = restaurantRepository.findByKakaoPlaceId(RECOMMEND_AGAIN_PLACE_ID)
                .orElseGet(() -> restaurantRepository.save(Restaurant.builder()
                        .kakaoPlaceId(RECOMMEND_AGAIN_PLACE_ID)
                        .name("재투표")
                        .category("SYSTEM")
                        .build()));
        roundCandidateRepository.save(RoundCandidate.builder()
                .round(round)
                .restaurant(recommendAgain)
                .rankNo(4)
                .reason("마음에 드는 후보가 없으면 새로운 식당 3곳을 추천받습니다.")
                .build());

        return round;
    }

    private Restaurant upsertRestaurant(RestaurantRecommendation recommendation) {
        return restaurantRepository.findByKakaoPlaceId(recommendation.id())
                .orElseGet(() -> restaurantRepository.save(Restaurant.builder()
                        .kakaoPlaceId(recommendation.id())
                        .name(recommendation.name())
                        .category(recommendation.category())
                        .roadAddress(recommendation.roadAddress())
                        .address(recommendation.address())
                        .latitude(recommendation.latitude() == null ? null : BigDecimal.valueOf(recommendation.latitude()))
                        .longitude(recommendation.longitude() == null ? null : BigDecimal.valueOf(recommendation.longitude()))
                        .build()));
    }

    private List<String> derivePreviouslyRecommendedRestaurantIds(Long meetupId) {
        return roundCandidateRepository.findByRound_Meetup_Id(meetupId).stream()
                .map(candidate -> candidate.getRestaurant().getKakaoPlaceId())
                .distinct()
                .toList();
    }

    // 재추천 시 같은 참여자가 다시 제출하면 기존 선호를 최신값으로 덮어쓴다.
    private void persistPreferences(Meetup meetup, List<PersonalOptionRequest> personalOptions) {
        for (PersonalOptionRequest option : personalOptions) {
            MeetupParticipant participant = findOrCreateParticipant(meetup, option.participantId());
            participantPreferenceUpserter.upsert(participant, option);
        }
    }

    private MeetupParticipant findOrCreateParticipant(Meetup meetup, Long memberId) {
        return meetupParticipantRepository.findByMeetupIdAndUserId(meetup.getId(), memberId)
                .orElseGet(() -> {
                    Member member = memberRepository.findById(memberId)
                            .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 참여자입니다: " + memberId));
                    return meetupParticipantRepository.save(MeetupParticipant.builder()
                            .meetup(meetup)
                            .user(member)
                            .submissionStatus(SubmissionStatus.SUBMITTED)
                            .confirmedForAi(true)
                            .build());
                });
    }

    private void requireMembership(Meetup meetup, Long memberId) {
        if (!chatRoomMemberRepository.existsByChatRoomIdAndUserId(meetup.getChatRoom().getId(), memberId)) {
            throw new IllegalArgumentException("채팅방 참여자만 추천을 요청할 수 있습니다.");
        }
    }

    private Meetup findMeetup(Long meetupId) {
        return meetupRepository.findById(meetupId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 모임입니다: " + meetupId));
    }
}
