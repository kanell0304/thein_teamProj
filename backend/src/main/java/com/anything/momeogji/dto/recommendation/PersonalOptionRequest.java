package com.anything.momeogji.dto.recommendation;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * 투표 참가자 개인이 입력/선택하는 옵션. 참여자 1명당 1건씩 리스트로 담아 보낸다.
 * (참여자 수는 이 레코드 리스트의 크기로 판별하므로 별도 인원수 필드는 없다)
 *
 * @param participantId       이 선호를 제출하는 실제 회원 ID. 존재하는 회원이어야 하며, 개인 선호(ParticipantPreference)가
 *                             이 값으로 저장된다. 채팅방 멤버십까지는 요구하지 않는다(투표할 때만 멤버십을 확인함).
 * @param walkMinutes         목적지 좌표 기준 도보로 이동 가능한 시간(분). 예: 10 → 도보 10분 이내 음식점만 후보로 검색.
 * @param preferredCategories 선호 검색 키워드 목록. 메뉴·카테고리·음식점명을 최대 3개까지 받고,
 *                            참여자 전체를 합산해 카카오 후보 검색과 AI 조건 우선순위를 정한다.
 * @param budgetLimit         1인당 지출 가능 금액 상한(원). 비워두면(null) 예산 제한 없음으로 처리된다.
 * @param parkingNeeded       자차로 이동해 주차가 필요한지 여부.
 * @param excludedFoods       못 먹거나 제외하고 싶은 음식/재료. 예: ["고수", "해산물"]. 참여자 중 한 명이라도 제외하면 후보에서 빠진다.
 * @param atmosphere          선호하는 분위기. 예: "룸", "개방형". 비워두면(null) 상관없음으로 처리된다.
 */
public record PersonalOptionRequest(
        @NotNull Long participantId,
        @NotNull @Min(1) Integer walkMinutes,
        @NotEmpty @Size(max = 3) List<@NotBlank String> preferredCategories,
        Integer budgetLimit,
        boolean parkingNeeded,
        List<String> excludedFoods,
        String atmosphere
) {
    public PersonalOptionRequest {
        excludedFoods = excludedFoods == null ? List.of() : List.copyOf(excludedFoods);
        preferredCategories = preferredCategories == null
                ? List.of()
                : preferredCategories.stream().distinct().toList();
    }
}
