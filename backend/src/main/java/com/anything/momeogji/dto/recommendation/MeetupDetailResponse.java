package com.anything.momeogji.dto.recommendation;

import java.time.LocalDateTime;

/** GET /api/meetups/{meetupId} 응답. 재접속한 클라이언트가 웹소켓 이벤트를 놓쳐도 현재 상태를 그대로 조회할 수 있다. */
public record MeetupDetailResponse(
        Long meetupId,
        Long chatRoomId,
        String status,
        CommonOptionRequest commonOption,
        RoundResponse latestRound,
        LocalDateTime personalOptionDeadlineAt,
        LocalDateTime voteDeadlineAt,
        Integer voteDurationMinutes,
        Long hostMemberId,
        FinalNoticeResponse finalNotice
) {
}
