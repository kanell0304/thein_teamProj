package com.anything.momeogji.dto.recommendation;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * 투표 참가자 개인이 입력/선택하는 옵션. 참여자 수는 이 레코드 리스트의 크기로 판별한다.
 */
public record PersonalOptionRequest(
        @NotBlank String participantId,
        @NotNull @Min(1) Integer walkMinutes,
        @NotEmpty List<String> preferredCategories,
        Integer budgetLimit,
        boolean parkingNeeded,
        List<String> excludedFoods,
        String atmosphere
) {
    public PersonalOptionRequest {
        excludedFoods = excludedFoods == null ? List.of() : List.copyOf(excludedFoods);
        preferredCategories = preferredCategories == null ? List.of() : List.copyOf(preferredCategories);
    }
}
