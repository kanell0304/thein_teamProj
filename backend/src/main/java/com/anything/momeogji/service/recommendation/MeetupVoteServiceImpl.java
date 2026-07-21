package com.anything.momeogji.service.recommendation;

import com.anything.momeogji.dto.recommendation.RoundResponse;
import com.anything.momeogji.entity.Member;
import com.anything.momeogji.entity.recommendation.Meetup;
import com.anything.momeogji.entity.recommendation.MeetupParticipant;
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
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

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
    private final RecommendationEventPublisher eventPublisher;
    private final RoundResponseAssembler roundResponseAssembler;

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

        return broadcastAndReturn(candidate.getRound(), meetup);
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
        RecommendationRound round = recommendationRoundRepository.findById(roundId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 추천 회차입니다: " + roundId));
        if (!round.getMeetup().getId().equals(meetupId)) {
            throw new IllegalArgumentException("해당 모임의 회차가 아닙니다: " + roundId);
        }

        RoundCandidate candidate = roundCandidateRepository.findById(roundCandidateId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 추천 후보입니다: " + roundCandidateId));
        if (!candidate.getRound().getId().equals(roundId)) {
            throw new IllegalArgumentException("해당 회차의 후보가 아닙니다: " + roundCandidateId);
        }

        return candidate;
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
