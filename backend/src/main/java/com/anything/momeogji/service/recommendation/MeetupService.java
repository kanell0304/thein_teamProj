package com.anything.momeogji.service.recommendation;

import com.anything.momeogji.dto.recommendation.CommonOptionRequest;
import com.anything.momeogji.dto.recommendation.MeetupDetailResponse;
import com.anything.momeogji.dto.recommendation.MeetupResponse;
import com.anything.momeogji.dto.recommendation.ParticipantSummaryResponse;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface MeetupService {

    /**
     * 채팅방 참여자만 모임을 만들 수 있다. 만든 사람이 호스트가 된다. voteDeadlineAt은 없으면(null) 마감 없이 진행한다.
     * participantIds로 지정한 사람들은 이 호출 즉시 PENDING 상태의 참여자로 등록되고, 초대 이벤트가 브로드캐스트된다.
     */
    MeetupResponse createMeetup(Long chatRoomId, CommonOptionRequest commonOption, LocalDateTime voteDeadlineAt,
                                 Integer voteDurationMinutes,
                                 List<Long> participantIds, Long hostMemberId);

    /** 재접속한 클라이언트가 현재 모임 상태(+최신 회차, 실시간 득표수, 확정 시 최종공지)를 조회할 때 쓴다. */
    MeetupDetailResponse getMeetupDetail(Long meetupId);

    /** 모임 참여자와 각자의 개인 선호 제출 상태를 조회한다. */
    List<ParticipantSummaryResponse> listParticipants(Long meetupId);

    /** 이 채팅방에서 가장 최근에 만들어진 모임을 조회한다. 새로고침/재접속한 클라이언트가 놓친 웹소켓 이벤트를 복원할 때 쓴다. */
    Optional<MeetupDetailResponse> getActiveMeetupForChatRoom(Long chatRoomId);
}
