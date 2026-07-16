package com.anything.momeogji.dto.recommendation;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record RecommendationRequest(
        @NotNull @Valid CommonOptionRequest commonOption,
        @NotEmpty @Valid List<PersonalOptionRequest> personalOptions,
        /** 재추천 시 이전에 추천됐던 음식점의 id(RestaurantRecommendation.id)를 넘기면 후보 검색 단계에서 제외한다. */
        List<String> excludedRestaurantIds,
        /** 재추천 시 사용자가 직접 입력하는 우선순위(예: "가성비 위주로", "웨이팅 적은 곳"). 없으면 null/빈 문자열. */
        String preferenceNote
) {
    public RecommendationRequest {
        excludedRestaurantIds = excludedRestaurantIds == null ? List.of() : List.copyOf(excludedRestaurantIds);
    }
}
