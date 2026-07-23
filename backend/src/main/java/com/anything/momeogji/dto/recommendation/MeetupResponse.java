package com.anything.momeogji.dto.recommendation;

import java.time.LocalDateTime;

public record MeetupResponse(
        Long id,
        Long chatRoomId,
        String status,
        CommonOptionRequest commonOption,
        LocalDateTime personalOptionDeadlineAt,
        LocalDateTime voteDeadlineAt,
        Integer voteDurationMinutes,
        Long hostMemberId
) {
}
