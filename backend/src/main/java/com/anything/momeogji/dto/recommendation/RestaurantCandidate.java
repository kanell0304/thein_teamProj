package com.anything.momeogji.dto.recommendation;

/** 카카오 키워드 검색으로 얻은 실제 음식점 후보. AI는 이 목록 안에서만 골라야 한다. */
public record RestaurantCandidate(
        String id,
        String name,
        String category,
        String roadAddress,
        String address,
        Double latitude,
        Double longitude,
        Integer distanceMeters
) {
}
