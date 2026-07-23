package com.anything.momeogji.mydata.transform.model;

/**
 * 카카오 로컬의 장소명과 전체 세부 카테고리를 묶은 MyData 최종 장소 매칭 값이다.
 *
 * <p>가맹점 집계 직후에는 {@link #unclassified()}로 아직 장소를 찾지 않은 상태를 표현한다.
 * 장소 분류 단계는 이름 매칭과 목적별 카테고리 필터를 통과한 경우에만 두 값을 함께 채우며,
 * 미매칭 결과는 최종 {@code TransformedUserMyData} 목록에서 제외한다.</p>
 *
 * @param placeName 카카오에 등록된 장소명. 분류 전이면 {@code null}
 * @param categoryName {@code 음식점 > 중식 > 중화요리} 형태의 전체 카테고리 경로. 분류 전이면 {@code null}
 */
public record KakaoPlaceMatchData(
        String placeName,
        String categoryName
) {

    /**
     * 장소명과 카테고리가 함께 존재하거나 함께 비어 있는지 검증한다.
     */
    public KakaoPlaceMatchData {
        // 선택적으로 전달된 문자열은 값이 있다면 공백이 아닌지 검증한다.
        validateOptionalText(placeName, "placeName");
        validateOptionalText(categoryName, "categoryName");

        // 장소명과 카테고리는 같은 카카오 응답에서 얻으므로 존재 여부가 같아야 한다.
        if ((placeName == null) != (categoryName == null)) {
            throw new IllegalArgumentException(
                    "placeName과 categoryName은 함께 존재하거나 함께 null이어야 합니다."
            );
        }
    }

    /**
     * 가맹점 집계는 끝났지만 카카오 장소 검색은 아직 수행하지 않은 상태를 만든다.
     *
     * @return 장소명과 카테고리가 모두 없는 분류 전 값
     */
    public static KakaoPlaceMatchData unclassified() {
        return new KakaoPlaceMatchData(null, null);
    }

    /**
     * 선택 문자열이 전달된 경우 공백 문자열이 아닌지 확인한다.
     *
     * @param value 검사할 선택 문자열
     * @param fieldName 오류 메시지에 표시할 필드명
     */
    private static void validateOptionalText(String value, String fieldName) {
        if (value != null && value.isBlank()) {
            throw new IllegalArgumentException(fieldName + "은(는) 공백일 수 없습니다.");
        }
    }
}
