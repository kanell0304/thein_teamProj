package com.anything.momeogji.service.recommendation;

import com.anything.momeogji.dto.recommendation.CommonOptionRequest;
import com.anything.momeogji.dto.recommendation.FinalNoticeResponse;
import com.anything.momeogji.dto.recommendation.MeetupDetailResponse;
import com.anything.momeogji.dto.recommendation.MeetupInvitationEvent;
import com.anything.momeogji.dto.recommendation.MeetupResponse;
import com.anything.momeogji.dto.recommendation.ParticipantSummaryResponse;
import com.anything.momeogji.dto.recommendation.RoundResponse;
import com.anything.momeogji.entity.ChatRoom;
import com.anything.momeogji.entity.Member;
import com.anything.momeogji.entity.recommendation.FinalNotice;
import com.anything.momeogji.entity.recommendation.Meetup;
import com.anything.momeogji.entity.recommendation.MeetupParticipant;
import com.anything.momeogji.entity.recommendation.MeetupStatus;
import com.anything.momeogji.entity.recommendation.Restaurant;
import com.anything.momeogji.entity.recommendation.SubmissionStatus;
import com.anything.momeogji.repository.ChatRoomMemberRepository;
import com.anything.momeogji.repository.ChatRoomRepository;
import com.anything.momeogji.repository.FinalNoticeRepository;
import com.anything.momeogji.repository.MeetupParticipantRepository;
import com.anything.momeogji.repository.MeetupRepository;
import com.anything.momeogji.repository.MemberRepository;
import com.anything.momeogji.repository.RecommendationRoundRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class MeetupServiceImpl implements MeetupService {

    private final MeetupRepository meetupRepository;
    private final ChatRoomRepository chatRoomRepository;
    private final ChatRoomMemberRepository chatRoomMemberRepository;
    private final MemberRepository memberRepository;
    private final MeetupParticipantRepository meetupParticipantRepository;
    private final RecommendationRoundRepository recommendationRoundRepository;
    private final FinalNoticeRepository finalNoticeRepository;
    private final RoundResponseAssembler roundResponseAssembler;
    private final RecommendationEventPublisher eventPublisher;

    @Override
    @Transactional
    public MeetupResponse createMeetup(Long chatRoomId, CommonOptionRequest commonOption,
                                        LocalDateTime personalOptionDeadlineAt, LocalDateTime voteDeadlineAt,
                                        Integer voteDurationMinutes,
                                        List<Long> participantIds, Long hostMemberId) {
        ChatRoom chatRoom = findChatRoom(chatRoomId);
        Member host = findMember(hostMemberId);
        requireMembership(chatRoomId, hostMemberId);
        participantIds.forEach(participantId -> requireMembership(chatRoomId, participantId));

        Meetup meetup = meetupRepository.save(Meetup.builder()
                .chatRoom(chatRoom)
                .hostUser(host)
                .status(MeetupStatus.PREFERENCE_COLLECTING)
                .destinationName(commonOption.destinationName())
                .destinationLatitude(BigDecimal.valueOf(commonOption.destinationLatitude()))
                .destinationLongitude(BigDecimal.valueOf(commonOption.destinationLongitude()))
                .meetingTime(commonOption.meetingTime())
                .purpose(commonOption.purpose())
                .personalOptionDeadlineAt(personalOptionDeadlineAt)
                .voteDeadlineAt(voteDeadlineAt)
                .voteDurationMinutes(voteDurationMinutes)
                .build());

        for (Long participantId : participantIds) {
            Member participantMember = findMember(participantId);
            meetupParticipantRepository.save(MeetupParticipant.builder()
                    .meetup(meetup)
                    .user(participantMember)
                    .submissionStatus(SubmissionStatus.PENDING)
                    .confirmedForAi(false)
                    .build());
        }

        MeetupResponse response = toResponse(meetup);
        eventPublisher.meetupInvitationSent(chatRoomId,
                new MeetupInvitationEvent(toDetailResponse(meetup, null), listParticipants(meetup.getId())));
        return response;
    }

    @Override
    @Transactional(readOnly = true)
    public MeetupDetailResponse getMeetupDetail(Long meetupId) {
        Meetup meetup = findMeetup(meetupId);
        RoundResponse latestRound = recommendationRoundRepository.findFirstByMeetupIdOrderByRoundNoDesc(meetupId)
                .map(roundResponseAssembler::assemble)
                .orElse(null);

        return toDetailResponse(meetup, latestRound);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ParticipantSummaryResponse> listParticipants(Long meetupId) {
        Meetup meetup = findMeetup(meetupId);
        return meetupParticipantRepository.findByMeetupId(meetupId).stream()
                .map(participant -> new ParticipantSummaryResponse(
                        participant.getId(),
                        participant.getUser().getId(),
                        participant.getUser().getNickname(),
                        participant.getSubmissionStatus().name(),
                        participant.getUser().getId().equals(meetup.getHostUser().getId())
                ))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<MeetupDetailResponse> getActiveMeetupForChatRoom(Long chatRoomId) {
        // 종료·만료된 모임은 재접속 시 현재 진행 중인 모임으로 복원하지 않는다.
        List<MeetupStatus> activeStatuses = List.of(
                MeetupStatus.DRAFT,
                MeetupStatus.PARTICIPANT_CONFIRMING,
                MeetupStatus.PREFERENCE_COLLECTING,
                MeetupStatus.RECOMMENDING,
                MeetupStatus.VOTING
        );
        return meetupRepository.findFirstByChatRoomIdAndStatusInOrderByIdDesc(chatRoomId, activeStatuses)
                .map(meetup -> {
                    RoundResponse latestRound = recommendationRoundRepository.findFirstByMeetupIdOrderByRoundNoDesc(meetup.getId())
                            .map(roundResponseAssembler::assemble)
                            .orElse(null);
                    return toDetailResponse(meetup, latestRound);
                });
    }

    private MeetupResponse toResponse(Meetup meetup) {
        return new MeetupResponse(meetup.getId(), meetup.getChatRoom().getId(), meetup.getStatus().name(),
                MeetupCommonOptionMapper.toCommonOption(meetup), meetup.getPersonalOptionDeadlineAt(),
                meetup.getVoteDeadlineAt(), meetup.getVoteDurationMinutes(), meetup.getHostUser().getId());
    }

    private MeetupDetailResponse toDetailResponse(Meetup meetup, RoundResponse latestRound) {
        FinalNoticeResponse finalNotice = meetup.getStatus() == MeetupStatus.FINALIZED
                ? finalNoticeRepository.findByMeetupId(meetup.getId()).map(notice -> toFinalNoticeResponse(notice, meetup.getId())).orElse(null)
                : null;

        return new MeetupDetailResponse(
                meetup.getId(),
                meetup.getChatRoom().getId(),
                meetup.getStatus().name(),
                MeetupCommonOptionMapper.toCommonOption(meetup),
                latestRound,
                meetup.getPersonalOptionDeadlineAt(),
                meetup.getVoteDeadlineAt(),
                meetup.getVoteDurationMinutes(),
                meetup.getHostUser().getId(),
                finalNotice
        );
    }

    private FinalNoticeResponse toFinalNoticeResponse(FinalNotice finalNotice, Long meetupId) {
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

    private void requireMembership(Long chatRoomId, Long memberId) {
        if (!chatRoomMemberRepository.existsByChatRoomIdAndUserId(chatRoomId, memberId)) {
            throw new IllegalArgumentException("채팅방 참여자만 모임을 만들 수 있습니다.");
        }
    }

    private ChatRoom findChatRoom(Long chatRoomId) {
        return chatRoomRepository.findById(chatRoomId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 채팅방입니다: " + chatRoomId));
    }

    private Member findMember(Long memberId) {
        return memberRepository.findById(memberId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 사용자입니다: " + memberId));
    }

    private Meetup findMeetup(Long meetupId) {
        return meetupRepository.findById(meetupId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 모임입니다: " + meetupId));
    }
}
