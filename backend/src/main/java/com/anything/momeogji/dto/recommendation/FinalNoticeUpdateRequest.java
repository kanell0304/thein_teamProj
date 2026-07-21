package com.anything.momeogji.dto.recommendation;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;

/** 확정된 최종공지를 수정할 때 보내는 요청. 지금은 약속시간만 수정할 수 있다. */
public record FinalNoticeUpdateRequest(
        @NotNull LocalDateTime meetingDatetime
) {
}
