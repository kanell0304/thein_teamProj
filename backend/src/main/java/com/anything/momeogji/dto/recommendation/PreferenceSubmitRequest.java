package com.anything.momeogji.dto.recommendation;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * 초대받은 참여자 본인이 제출하는 개인 선호다.
 * participantId는 JWT 인증 정보에서 구해 별도로 받지 않으며, 마이데이터 동의도 인증된 본인의 값만 저장한다.
 * preferredCategories는 현재 계약 이름을 유지하지만 메뉴·카테고리·음식점명을 포함하는 검색 키워드이며 최대 3개다.
 */
public record PreferenceSubmitRequest(
        @NotNull @Min(1) Integer walkMinutes,
        @NotEmpty @Size(max = 3) List<@NotBlank String> preferredCategories,
        Integer budgetLimit,
        boolean parkingNeeded,
        List<String> excludedFoods,
        String atmosphere,
        boolean myDataConsent
) {
    public PreferenceSubmitRequest {
        excludedFoods = excludedFoods == null ? List.of() : List.copyOf(excludedFoods);
        preferredCategories = preferredCategories == null
                ? List.of()
                : preferredCategories.stream().distinct().toList();
    }
}
