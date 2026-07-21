package com.anything.momeogji.service.recommendation;

import com.anything.momeogji.dto.recommendation.CommonOptionRequest;
import com.anything.momeogji.dto.recommendation.MeetupDetailResponse;
import com.anything.momeogji.dto.recommendation.MeetupResponse;

import java.time.LocalDateTime;

public interface MeetupService {

    /** 채팅방 참여자만 모임을 만들 수 있다. 만든 사람이 호스트가 된다. voteDeadlineAt은 없으면(null) 마감 없이 진행한다. */
    MeetupResponse createMeetup(Long chatRoomId, CommonOptionRequest commonOption, LocalDateTime voteDeadlineAt, Long hostMemberId);

    /** 재접속한 클라이언트가 현재 모임 상태(+최신 회차, 실시간 득표수)를 조회할 때 쓴다. */
    MeetupDetailResponse getMeetupDetail(Long meetupId);
}
