package com.anything.momeogji.service.recommendation;

import com.anything.momeogji.dto.recommendation.RoundResponse;
import com.anything.momeogji.dto.recommendation.PersonalOptionRequest;
import com.anything.momeogji.dto.recommendation.VoteSelectionRequest;
import com.anything.momeogji.entity.Member;
import com.anything.momeogji.entity.recommendation.Meetup;
import com.anything.momeogji.entity.recommendation.MeetupParticipant;
import com.anything.momeogji.entity.recommendation.MeetupStatus;
import com.anything.momeogji.entity.recommendation.RecommendationRound;
import com.anything.momeogji.entity.recommendation.RoundCandidate;
import com.anything.momeogji.entity.recommendation.SubmissionStatus;
import com.anything.momeogji.entity.recommendation.Vote;
import com.anything.momeogji.repository.ChatRoomMemberRepository;
import com.anything.momeogji.repository.MeetupParticipantRepository;
import com.anything.momeogji.repository.MeetupRepository;
import com.anything.momeogji.repository.MemberRepository;
import com.anything.momeogji.repository.RecommendationRoundRepository;
import com.anything.momeogji.repository.RoundCandidateRepository;
import com.anything.momeogji.repository.VoteRepository;
import com.anything.momeogji.repository.ParticipantPreferenceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class MeetupVoteServiceImpl implements MeetupVoteService {

    private final MeetupRepository meetupRepository;
    private final RecommendationRoundRepository recommendationRoundRepository;
    private final RoundCandidateRepository roundCandidateRepository;
    private final MeetupParticipantRepository meetupParticipantRepository;
    private final MemberRepository memberRepository;
    private final ChatRoomMemberRepository chatRoomMemberRepository;
    private final VoteRepository voteRepository;
    private final ParticipantPreferenceRepository participantPreferenceRepository;
    private final RecommendationEventPublisher eventPublisher;
    private final RoundResponseAssembler roundResponseAssembler;
    private final MeetupFinalizeService meetupFinalizeService;
    private final RecommendationRoundService recommendationRoundService;

    @Override
    @Transactional
    public RoundResponse castVote(Long meetupId, Long roundId, Long roundCandidateId, Long callerId) {
        RoundCandidate candidate = resolveCandidate(meetupId, roundId, roundCandidateId);
        Meetup meetup = candidate.getRound().getMeetup();
        requireMembership(meetup, callerId);
        requireBeforeDeadline(meetup);
        MeetupParticipant participant = findOrCreateParticipant(meetup, callerId);

        boolean alreadyVoted = voteRepository.findByRoundCandidateIdAndMeetupParticipantId(roundCandidateId, participant.getId()).isPresent();
        if (!alreadyVoted) {
            voteRepository.save(Vote.builder()
                    .roundCandidate(candidate)
                    .meetupParticipant(participant)
                    .votedAt(LocalDateTime.now())
                    .build());
        }

        RoundResponse response = broadcastAndReturn(candidate.getRound(), meetup);
        return resolveCompletedRound(meetup, candidate.getRound(),
                roundCandidateRepository.findByRoundId(candidate.getRound().getId()), response);
    }

    @Override
    @Transactional
    public RoundResponse retractVote(Long meetupId, Long roundId, Long roundCandidateId, Long callerId) {
        RoundCandidate candidate = resolveCandidate(meetupId, roundId, roundCandidateId);
        Meetup meetup = candidate.getRound().getMeetup();
        requireMembership(meetup, callerId);
        requireBeforeDeadline(meetup);

        meetupParticipantRepository.findByMeetupIdAndUserId(meetupId, callerId)
                .ifPresent(participant -> voteRepository.deleteByRoundCandidateIdAndMeetupParticipantId(roundCandidateId, participant.getId()));

        return broadcastAndReturn(candidate.getRound(), meetup);
    }

    // 네 후보의 복수 선택을 한 요청으로 교체한 뒤에만 완료 여부를 판단한다.
    @Override
    @Transactional
    public RoundResponse replaceVotes(Long meetupId, Long roundId, VoteSelectionRequest request, Long callerId) {
        RecommendationRound round = resolveRound(meetupId, roundId);
        Meetup meetup = round.getMeetup();
        requireMembership(meetup, callerId);
        requireBeforeDeadline(meetup);
        if (meetup.getStatus() == MeetupStatus.FINALIZED) {
            throw new IllegalArgumentException("이미 종료된 투표입니다.");
        }

        List<RoundCandidate> roundCandidates = roundCandidateRepository.findByRoundId(roundId);
        Set<Long> validCandidateIds = roundCandidates.stream().map(RoundCandidate::getId).collect(java.util.stream.Collectors.toSet());
        if (request.candidateIds().stream().anyMatch(candidateId -> !validCandidateIds.contains(candidateId))) {
            throw new IllegalArgumentException("현재 회차에 없는 투표 후보가 포함되어 있습니다.");
        }

        MeetupParticipant participant = findOrCreateParticipant(meetup, callerId);
        List<Vote> previousVotes = voteRepository
                .findByRoundCandidateRoundIdAndMeetupParticipantId(roundId, participant.getId());
        voteRepository.deleteAll(previousVotes);

        for (Long candidateId : request.candidateIds()) {
            RoundCandidate candidate = roundCandidates.stream()
                    .filter(item -> item.getId().equals(candidateId))
                    .findFirst()
                    .orElseThrow();
            voteRepository.save(Vote.builder()
                    .roundCandidate(candidate)
                    .meetupParticipant(participant)
                    .votedAt(LocalDateTime.now())
                    .build());
        }
        voteRepository.flush();

        RoundResponse response = broadcastAndReturn(round, meetup);
        return resolveCompletedRound(meetup, round, roundCandidates, response);
    }

    // 재투표가 단독 1위면 이전 후보가 자동 제외된 새 회차를 만들고, 공동 1위면 음식점을 우선 확정한다.
    private RoundResponse resolveCompletedRound(Meetup meetup, RecommendationRound round,
                                                List<RoundCandidate> candidates, RoundResponse currentResponse) {
        long submittedCount = meetupParticipantRepository
                .countByMeetupIdAndSubmissionStatus(meetup.getId(), SubmissionStatus.SUBMITTED);
        if (submittedCount == 0 || voteRepository.countDistinctVotersByRoundId(round.getId()) < submittedCount) {
            return currentResponse;
        }

        RoundCandidate recommendAgain = candidates.stream()
                .filter(candidate -> RecommendationRoundServiceImpl.RECOMMEND_AGAIN_PLACE_ID
                        .equals(candidate.getRestaurant().getKakaoPlaceId()))
                .findFirst()
                .orElse(null);
        long recommendAgainVotes = recommendAgain == null ? 0 : voteRepository.countByRoundCandidateId(recommendAgain.getId());
        long bestRestaurantVotes = candidates.stream()
                .filter(candidate -> candidate != recommendAgain)
                .mapToLong(candidate -> voteRepository.countByRoundCandidateId(candidate.getId()))
                .max()
                .orElse(0);

        if (recommendAgain != null && recommendAgainVotes > bestRestaurantVotes) {
            List<PersonalOptionRequest> preferences = participantPreferenceRepository
                    .findByMeetupParticipant_Meetup_Id(meetup.getId()).stream()
                    .map(preference -> new PersonalOptionRequest(
                            preference.getMeetupParticipant().getUser().getId(),
                            preference.getWalkMinutes(),
                            preference.getPreferredCategories(),
                            preference.getBudgetLimit(),
                            preference.isParkingNeeded(),
                            preference.getExcludedFoods(),
                            preference.getAtmosphere()))
                    .toList();
            return recommendationRoundService.triggerAutoRecommendation(meetup, preferences);
        }

        meetupFinalizeService.finalizeInternal(meetup);
        return currentResponse;
    }

    private RoundResponse broadcastAndReturn(RecommendationRound round, Meetup meetup) {
        RoundResponse response = roundResponseAssembler.assemble(round);
        eventPublisher.voteTallied(meetup.getChatRoom().getId(), response);
        return response;
    }

    private MeetupParticipant findOrCreateParticipant(Meetup meetup, Long memberId) {
        return meetupParticipantRepository.findByMeetupIdAndUserId(meetup.getId(), memberId)
                .orElseGet(() -> {
                    Member member = memberRepository.findById(memberId)
                            .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 사용자입니다: " + memberId));
                    return meetupParticipantRepository.save(MeetupParticipant.builder()
                            .meetup(meetup)
                            .user(member)
                            .submissionStatus(SubmissionStatus.SUBMITTED)
                            .confirmedForAi(true)
                            .build());
                });
    }

    private RoundCandidate resolveCandidate(Long meetupId, Long roundId, Long roundCandidateId) {
        RecommendationRound round = resolveRound(meetupId, roundId);

        RoundCandidate candidate = roundCandidateRepository.findById(roundCandidateId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 추천 후보입니다: " + roundCandidateId));
        if (!candidate.getRound().getId().equals(roundId)) {
            throw new IllegalArgumentException("해당 회차의 후보가 아닙니다: " + roundCandidateId);
        }

        return candidate;
    }

    private RecommendationRound resolveRound(Long meetupId, Long roundId) {
        RecommendationRound round = recommendationRoundRepository.findById(roundId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 추천 회차입니다: " + roundId));
        if (!round.getMeetup().getId().equals(meetupId)) {
            throw new IllegalArgumentException("해당 모임의 회차가 아닙니다: " + roundId);
        }
        return round;
    }

    private void requireMembership(Meetup meetup, Long memberId) {
        if (!chatRoomMemberRepository.existsByChatRoomIdAndUserId(meetup.getChatRoom().getId(), memberId)) {
            throw new IllegalArgumentException("채팅방 참여자만 투표할 수 있습니다.");
        }
    }

    private void requireBeforeDeadline(Meetup meetup) {
        LocalDateTime deadline = meetup.getVoteDeadlineAt();
        if (deadline != null && LocalDateTime.now().isAfter(deadline)) {
            throw new IllegalArgumentException("투표 마감 시간이 지나 더 이상 투표할 수 없습니다.");
        }
    }
}
