package com.anything.momeogji.dto.recommendation;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;

/**
 * 주최자가 입력하는 공통 옵션. 모임 전체에 한 번만 적용된다.
 *
 * @param destinationName      목적지(만남 장소) 이름. 화면 표시용 라벨이며 검색에는 좌표가 쓰인다. 예: "강남역"
 * @param destinationLatitude  목적지 위도. 개인 옵션의 walkMinutes(도보 가능 거리)와 함께 음식점 검색 반경의 기준점이 된다.
 * @param destinationLongitude 목적지 경도.
 * @param meetingTime          약속 일시. ISO-8601 형식(예: "2026-07-20T12:00:00").
 * @param purpose               모임 목적. 예: "식사", "술자리", "회식", "미팅" 등 자유 텍스트.
 */
public record CommonOptionRequest(
        @NotBlank String destinationName,
        @NotNull Double destinationLatitude,
        @NotNull Double destinationLongitude,
        @NotNull LocalDateTime meetingTime,
        @NotBlank String purpose
) {
}
