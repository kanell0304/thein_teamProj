package com.anything.momeogji.mydata.processing.model;

/**
 * 마이데이터 가공 결과에서 AI 추천에 전달할 음식점명과 음식 카테고리만 보존한다.
 *
 * @param restaurantName 카카오 장소 검색으로 확정한 음식점명
 * @param foodCategory 카카오 전체 카테고리 경로에서 음식점·카페 대분류를 제거한 음식 카테고리
 */
public record MyDataRestaurantData(
        String restaurantName,
        String foodCategory
) {

    /**
     * AI 전달 항목에 필요한 두 문자열이 모두 존재하는지 검증한다.
     */
    public MyDataRestaurantData {
        // 음식점명은 카카오 장소 매칭이 완료된 결과만 허용한다.
        if (restaurantName == null || restaurantName.isBlank()) {
            throw new IllegalArgumentException("restaurantName은 필수입니다.");
        }

        // 대분류 제거 후에도 AI가 사용할 음식 카테고리가 남아 있어야 한다.
        if (foodCategory == null || foodCategory.isBlank()) {
            throw new IllegalArgumentException("foodCategory는 필수입니다.");
        }
    }
}
