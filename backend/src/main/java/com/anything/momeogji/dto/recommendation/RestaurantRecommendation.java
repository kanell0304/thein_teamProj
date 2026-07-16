package com.anything.momeogji.dto.recommendation;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * AI가 추천한 음식점 1건. 좌표/주소는 카카오 실검색 결과에서 그대로 채워지므로 신뢰할 수 있다.
 * id는 카카오 장소 id로, 재추천 요청의 excludedRestaurantIds에 그대로 되돌려주면 다음 후보 검색에서 제외된다.
 * imageUrl은 카카오 이미지 검색 결과(참고용)라 그 가게의 공식 사진이라는 보장은 없고, 못 찾으면 null이다.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record RestaurantRecommendation(
        String id,
        int rank,
        String name,
        String category,
        String roadAddress,
        String address,
        Double latitude,
        Double longitude,
        String reason,
        String imageUrl
) {
}
