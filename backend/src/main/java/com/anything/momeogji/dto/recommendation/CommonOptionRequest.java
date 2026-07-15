package com.anything.momeogji.dto.recommendation;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;

/**
 * 주최자가 입력하는 공통 옵션.
 * (destinationLatitude/destinationLongitude는 도보 가능 거리 계산의 기준 좌표로도 사용된다)
 */
public record CommonOptionRequest(
        @NotBlank String destinationName,
        @NotNull Double destinationLatitude,
        @NotNull Double destinationLongitude,
        @NotNull LocalDateTime meetingTime,
        @NotBlank String purpose
) {
}
