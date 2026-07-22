package com.anything.momeogji.service.recommendation;

import com.anything.momeogji.dto.recommendation.FinalNoticeResponse;
import com.anything.momeogji.dto.recommendation.FinalNoticeUpdateRequest;
import com.anything.momeogji.entity.Member;
import com.anything.momeogji.entity.recommendation.FinalNotice;
import com.anything.momeogji.entity.recommendation.FinalNoticeChangeLog;
import com.anything.momeogji.entity.recommendation.Meetup;
import com.anything.momeogji.entity.recommendation.RecommendationRound;
import com.anything.momeogji.entity.recommendation.Restaurant;
import com.anything.momeogji.entity.recommendation.RoundCandidate;
import com.anything.momeogji.repository.FinalNoticeChangeLogRepository;
import com.anything.momeogji.repository.FinalNoticeRepository;
import com.anything.momeogji.repository.MeetupParticipantRepository;
import com.anything.momeogji.repository.MeetupRepository;
import com.anything.momeogji.repository.MemberRepository;
import com.anything.momeogji.repository.RecommendationRoundRepository;
import com.anything.momeogji.repository.RoundCandidateRepository;
import com.anything.momeogji.repository.VoteRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Random;

@Service
public class MeetupFinalizeServiceImpl implements MeetupFinalizeService {

    private final MeetupRepository meetupRepository;
    private final RecommendationRoundRepository recommendationRoundRepository;
    private final RoundCandidateRepository roundCandidateRepository;
    private final VoteRepository voteRepository;
    private final MeetupParticipantRepository meetupParticipantRepository;
    private final MemberRepository memberRepository;
    private final FinalNoticeRepository finalNoticeRepository;
    private final FinalNoticeChangeLogRepository finalNoticeChangeLogRepository;
    private final RecommendationEventPublisher eventPublisher;
    private final Random random;

    @Autowired
    public MeetupFinalizeServiceImpl(MeetupRepository meetupRepository,
                                      RecommendationRoundRepository recommendationRoundRepository,
                                      RoundCandidateRepository roundCandidateRepository,
                                      VoteRepository voteRepository,
                                      MeetupParticipantRepository meetupParticipantRepository,
                                      MemberRepository memberRepository,
                                      FinalNoticeRepository finalNoticeRepository,
                                      FinalNoticeChangeLogRepository finalNoticeChangeLogRepository,
                                      RecommendationEventPublisher eventPublisher) {
        this(meetupRepository, recommendationRoundRepository, roundCandidateRepository, voteRepository,
                meetupParticipantRepository, memberRepository, finalNoticeRepository, finalNoticeChangeLogRepository,
                eventPublisher, new SecureRandom());
    }

    // 테스트에서 결정적인 결과를 검증할 수 있도록 Random을 주입받는 생성자(패키지 전용)
    MeetupFinalizeServiceImpl(MeetupRepository meetupRepository,
                               RecommendationRoundRepository recommendationRoundRepository,
                               RoundCandidateRepository roundCandidateRepository,
                               VoteRepository voteRepository,
                               MeetupParticipantRepository meetupParticipantRepository,
                               MemberRepository memberRepository,
                               FinalNoticeRepository finalNoticeRepository,
                               FinalNoticeChangeLogRepository finalNoticeChangeLogRepository,
                               RecommendationEventPublisher eventPublisher,
                               Random random) {
        this.meetupRepository = meetupRepository;
        this.recommendationRoundRepository = recommendationRoundRepository;
        this.roundCandidateRepository = roundCandidateRepository;
        this.voteRepository = voteRepository;
        this.meetupParticipantRepository = meetupParticipantRepository;
        this.memberRepository = memberRepository;
        this.finalNoticeRepository = finalNoticeRepository;
        this.finalNoticeChangeLogRepository = finalNoticeChangeLogRepository;
        this.eventPublisher = eventPublisher;
        this.random = random;
    }

    @Override
    @Transactional
    public FinalNoticeResponse finalize(Long meetupId, Long callerId) {
        Meetup meetup = findMeetup(meetupId);
        requireHost(meetup, callerId);
        return finalizeInternal(meetup);
    }

    @Override
    @Transactional
    public FinalNoticeResponse finalizeInternal(Meetup meetup) {
        Long meetupId = meetup.getId();
        RecommendationRound round = recommendationRoundRepository.findFirstByMeetupIdOrderByRoundNoDesc(meetupId)
                .orElseThrow(() -> new IllegalArgumentException("아직 추천 회차가 없어 확정할 수 없습니다: " + meetupId));

        List<RoundCandidate> candidates = roundCandidateRepository.findByRoundId(round.getId());
        if (candidates.isEmpty()) {
            throw new IllegalArgumentException("추천 후보가 없어 확정할 수 없습니다: " + round.getId());
        }

        RoundCandidate winner = pickWinner(candidates);

        FinalNotice finalNotice = finalNoticeRepository.save(FinalNotice.builder()
                .meetup(meetup)
                .restaurant(winner.getRestaurant())
                .meetingDatetime(meetup.getMeetingTime())
                .pinnedUntil(meetup.getMeetingTime())
                .imageUrl(winner.getImageUrl())
                .build());
        meetup.markFinalized();

        FinalNoticeResponse response = toResponse(finalNotice, meetupId);
        eventPublisher.finalNoticePublished(meetup.getChatRoom().getId(), response);
        return response;
    }

    @Override
    @Transactional
    public FinalNoticeResponse updateFinalNotice(Long meetupId, FinalNoticeUpdateRequest request, Long callerId) {
        Meetup meetup = findMeetup(meetupId);
        requireHost(meetup, callerId);

        FinalNotice finalNotice = finalNoticeRepository.findByMeetupId(meetupId)
                .orElseThrow(() -> new IllegalArgumentException("아직 확정되지 않은 모임입니다: " + meetupId));

        if (!finalNotice.getMeetingDatetime().equals(request.meetingDatetime())) {
            Member changedBy = findMember(callerId);
            finalNoticeChangeLogRepository.save(FinalNoticeChangeLog.builder()
                    .finalNotice(finalNotice)
                    .changedBy(changedBy)
                    .changedField("meetingDatetime")
                    .changedAt(LocalDateTime.now())
                    .build());
            finalNotice.updateMeetingDatetime(request.meetingDatetime());
        }

        FinalNoticeResponse response = toResponse(finalNotice, meetupId);
        eventPublisher.finalNoticePublished(meetup.getChatRoom().getId(), response);
        return response;
    }

    private FinalNoticeResponse toResponse(FinalNotice finalNotice, Long meetupId) {
        Restaurant restaurant = finalNotice.getRestaurant();
        int participantCount = (int) meetupParticipantRepository.countByMeetupId(meetupId);
        return new FinalNoticeResponse(
                restaurant.getName(),
                restaurant.getRoadAddress(),
                restaurant.getAddress(),
                restaurant.getLatitude() == null ? null : restaurant.getLatitude().doubleValue(),
                restaurant.getLongitude() == null ? null : restaurant.getLongitude().doubleValue(),
                finalNotice.getImageUrl(),
                participantCount,
                finalNotice.getMeetingDatetime()
        );
    }

    private RoundCandidate pickWinner(List<RoundCandidate> candidates) {
        List<RoundCandidate> restaurantCandidates = candidates.stream()
                .filter(candidate -> !RecommendationRoundServiceImpl.RECOMMEND_AGAIN_PLACE_ID
                        .equals(candidate.getRestaurant().getKakaoPlaceId()))
                .toList();
        if (restaurantCandidates.isEmpty()) {
            throw new IllegalArgumentException("확정할 음식점 후보가 없습니다.");
        }

        long maxVotes = restaurantCandidates.stream()
                .mapToLong(candidate -> voteRepository.countByRoundCandidateId(candidate.getId()))
                .max()
                .orElse(0);

        if (maxVotes == 0) {
            throw new IllegalArgumentException("아직 투표가 없어 확정할 수 없습니다.");
        }

        List<RoundCandidate> topCandidates = restaurantCandidates.stream()
                .filter(candidate -> voteRepository.countByRoundCandidateId(candidate.getId()) == maxVotes)
                .toList();

        return topCandidates.size() == 1 ? topCandidates.get(0) : topCandidates.get(random.nextInt(topCandidates.size()));
    }

    private void requireHost(Meetup meetup, Long callerId) {
        if (!meetup.getHostUser().getId().equals(callerId)) {
            throw new IllegalArgumentException("호스트만 최종 확정할 수 있습니다.");
        }
    }

    private Meetup findMeetup(Long meetupId) {
        return meetupRepository.findById(meetupId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 모임입니다: " + meetupId));
    }

    private Member findMember(Long memberId) {
        return memberRepository.findById(memberId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 사용자입니다: " + memberId));
    }
}
