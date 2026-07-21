package com.anything.momeogji.service.recommendation;

import com.anything.momeogji.dto.recommendation.CommonOptionRequest;
import com.anything.momeogji.dto.recommendation.MeetupDetailResponse;
import com.anything.momeogji.dto.recommendation.MeetupResponse;
import com.anything.momeogji.dto.recommendation.RoundResponse;
import com.anything.momeogji.entity.ChatRoom;
import com.anything.momeogji.entity.Member;
import com.anything.momeogji.entity.recommendation.Meetup;
import com.anything.momeogji.entity.recommendation.MeetupStatus;
import com.anything.momeogji.repository.ChatRoomMemberRepository;
import com.anything.momeogji.repository.ChatRoomRepository;
import com.anything.momeogji.repository.MeetupRepository;
import com.anything.momeogji.repository.MemberRepository;
import com.anything.momeogji.repository.RecommendationRoundRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class MeetupServiceImpl implements MeetupService {

    private final MeetupRepository meetupRepository;
    private final ChatRoomRepository chatRoomRepository;
    private final ChatRoomMemberRepository chatRoomMemberRepository;
    private final MemberRepository memberRepository;
    private final RecommendationRoundRepository recommendationRoundRepository;
    private final RoundResponseAssembler roundResponseAssembler;

    @Override
    @Transactional
    public MeetupResponse createMeetup(Long chatRoomId, CommonOptionRequest commonOption, LocalDateTime voteDeadlineAt, Long hostMemberId) {
        ChatRoom chatRoom = findChatRoom(chatRoomId);
        Member host = findMember(hostMemberId);
        requireMembership(chatRoomId, hostMemberId);

        Meetup meetup = meetupRepository.save(Meetup.builder()
                .chatRoom(chatRoom)
                .hostUser(host)
                .status(MeetupStatus.RECOMMENDING)
                .destinationName(commonOption.destinationName())
                .destinationLatitude(BigDecimal.valueOf(commonOption.destinationLatitude()))
                .destinationLongitude(BigDecimal.valueOf(commonOption.destinationLongitude()))
                .meetingTime(commonOption.meetingTime())
                .purpose(commonOption.purpose())
                .voteDeadlineAt(voteDeadlineAt)
                .build());

        return toResponse(meetup);
    }

    @Override
    @Transactional(readOnly = true)
    public MeetupDetailResponse getMeetupDetail(Long meetupId) {
        Meetup meetup = findMeetup(meetupId);
        RoundResponse latestRound = recommendationRoundRepository.findFirstByMeetupIdOrderByRoundNoDesc(meetupId)
                .map(roundResponseAssembler::assemble)
                .orElse(null);

        return new MeetupDetailResponse(
                meetup.getId(),
                meetup.getChatRoom().getId(),
                meetup.getStatus().name(),
                MeetupCommonOptionMapper.toCommonOption(meetup),
                latestRound,
                meetup.getVoteDeadlineAt()
        );
    }

    private MeetupResponse toResponse(Meetup meetup) {
        return new MeetupResponse(meetup.getId(), meetup.getChatRoom().getId(), meetup.getStatus().name(),
                MeetupCommonOptionMapper.toCommonOption(meetup), meetup.getVoteDeadlineAt());
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
