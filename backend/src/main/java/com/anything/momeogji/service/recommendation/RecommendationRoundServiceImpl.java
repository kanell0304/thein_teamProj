package com.anything.momeogji.service.recommendation;

import com.anything.momeogji.dto.recommendation.PersonalOptionRequest;
import com.anything.momeogji.dto.recommendation.RecommendationRequest;
import com.anything.momeogji.dto.recommendation.RecommendationResult;
import com.anything.momeogji.dto.recommendation.RestaurantRecommendation;
import com.anything.momeogji.dto.recommendation.RoundCreateRequest;
import com.anything.momeogji.dto.recommendation.RoundResponse;
import com.anything.momeogji.entity.recommendation.Meetup;
import com.anything.momeogji.entity.recommendation.RecommendationRound;
import com.anything.momeogji.entity.recommendation.RecommendationRoundStatus;
import com.anything.momeogji.entity.recommendation.Restaurant;
import com.anything.momeogji.entity.recommendation.RoundCandidate;
import com.anything.momeogji.mydata.processing.model.MyDataRestaurantData;
import com.anything.momeogji.repository.ChatRoomMemberRepository;
import com.anything.momeogji.repository.MeetupRepository;
import com.anything.momeogji.repository.RecommendationRoundRepository;
import com.anything.momeogji.repository.RestaurantRepository;
import com.anything.momeogji.repository.RoundCandidateRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class RecommendationRoundServiceImpl implements RecommendationRoundService {

    /** 네 번째 선택지를 일반 식당과 같은 후보/투표 구조로 다루기 위한 내부 장소 ID. */
    static final String RECOMMEND_AGAIN_PLACE_ID = "__RECOMMEND_AGAIN__";
    /** 개인 선택지의 "원하는 분위기"에서 이 키워드를 고르면, 본인 MyData 방문 카테고리를 오히려 피하고 싶다는 의미다. */
    private static final String NEW_FOOD_MOOD_KEYWORD = "새로운 메뉴";

    private final MeetupRepository meetupRepository;
    private final ChatRoomMemberRepository chatRoomMemberRepository;
    private final RecommendationRoundRepository recommendationRoundRepository;
    private final RoundCandidateRepository roundCandidateRepository;
    private final RestaurantRepository restaurantRepository;
    private final RestaurantRecommendationService restaurantRecommendationService;
    private final MeetupMyDataResultStore meetupMyDataResultStore;
    private final RecommendationEventPublisher eventPublisher;
    private final RoundResponseAssembler roundResponseAssembler;

    @Override
    @Transactional
    public RoundResponse createRound(Long meetupId, RoundCreateRequest request, Long callerId) {
        Meetup meetup = findMeetup(meetupId);
        requireMembership(meetup, callerId);
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

        // 참여자별로 MyData를 나눠서, "새로운 메뉴"를 원하는 사람의 것은 추천에 더하는 대신 오히려 피할 카테고리로 돌린다.
        List<PersonalOptionRequest> resolvedOptions = new ArrayList<>();
        List<MyDataRestaurantData> myDataForRecommendation = new ArrayList<>();
        for (PersonalOptionRequest option : personalOptions) {
            List<MyDataRestaurantData> participantMyData =
                    meetupMyDataResultStore.findAll(meetup.getId(), List.of(option.participantId()));

            if (seeksNewFood(option)) {
                resolvedOptions.add(withAvoidedCategories(option, participantMyData));
            } else {
                resolvedOptions.add(option);
                myDataForRecommendation.addAll(participantMyData);
            }
        }

        RecommendationRequest recommendationRequest = new RecommendationRequest(
                MeetupCommonOptionMapper.toCommonOption(meetup),
                resolvedOptions,
                excludedRestaurantIds,
                myDataForRecommendation,
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
        // 추천이 끝나 투표가 실제로 열린 시점부터 제한시간을 계산한다.
        meetup.startVoting();

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

    private boolean seeksNewFood(PersonalOptionRequest option) {
        return option.atmosphere() != null && option.atmosphere().contains(NEW_FOOD_MOOD_KEYWORD);
    }

    /** "새로운 메뉴"를 고른 참여자는 본인 MyData 방문 카테고리를 반대로 제외 목록에 추가한다. */
    private PersonalOptionRequest withAvoidedCategories(PersonalOptionRequest option,
                                                          List<MyDataRestaurantData> participantMyData) {
        List<String> avoidedCategories = participantMyData.stream()
                .map(MyDataRestaurantData::foodCategory)
                .map(MyDataCategoryMatcher::match)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (avoidedCategories.isEmpty()) {
            return option;
        }

        List<String> mergedExcludedFoods = new ArrayList<>(option.excludedFoods());
        mergedExcludedFoods.addAll(avoidedCategories);
        return new PersonalOptionRequest(option.participantId(), option.walkMinutes(), option.preferredCategories(),
                option.budgetLimit(), option.parkingNeeded(), mergedExcludedFoods, option.atmosphere());
    }

    private List<String> derivePreviouslyRecommendedRestaurantIds(Long meetupId) {
        return roundCandidateRepository.findByRound_Meetup_Id(meetupId).stream()
                .map(candidate -> candidate.getRestaurant().getKakaoPlaceId())
                .distinct()
                .toList();
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
