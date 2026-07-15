package com.anything.momeogji.dto.recommendation;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * AI가 추천한 음식점 1건. 좌표/주소는 카카오맵 표시 및 최종 공지에 그대로 사용된다.
 * AI가 확신할 수 없는 값은 null로 내려오도록 프롬프트에서 강제한다.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record RestaurantRecommendation(
        int rank,
        Tier tier,
        String name,
        String category,
        String roadAddress,
        String address,
        Double latitude,
        Double longitude,
        String reason
) {
}
