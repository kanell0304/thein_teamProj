package com.anything.momeogji.dto.chat;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.time.Instant;
import java.util.List;

/** 모먹지 참가자의 기능 시작 직전 대화를 분석하기 위한 요청. */
public record ChatMenuKeywordRequest(
        @NotNull Instant featureStartedAt,
        @NotEmpty List<@NotNull @Positive Long> participantIds
) {
}
