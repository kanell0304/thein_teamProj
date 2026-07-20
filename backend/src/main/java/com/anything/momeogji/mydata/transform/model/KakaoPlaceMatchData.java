package com.anything.momeogji.mydata.transform.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Set;

/**
 * 카카오 로컬의 장소 검색 결과 중 가맹점 분류에 최종적으로 채택한 정보를 표현한다.
 *
 * <p>검색하지 못했거나 이름이 일치하는 후보가 없으면 {@link #unmatched()}를 사용한다.
 * 장소를 찾았지만 최고 후보 사이에서 카테고리를 하나로 확정하지 못한 경우에는
 * 장소 정보와 신뢰도를 유지하면서 {@link #UNKNOWN_CATEGORY_CODE}를 사용할 수 있다.</p>
 *
 * <p>좌표는 카카오가 제공한 경도·위도 쌍이 모두 유효할 때만 보존한다.
 * 유효한 좌표는 결과 크기를 제한하기 위해 소수점 다섯째 자리까지 절삭한다.</p>
 *
 * @param categoryCode 음식점 {@code FD6}, 카페 {@code CE7}, 내부 미분류값 {@code UNKNOWN} 중 하나
 * @param placeId 선택한 카카오 장소 ID. 미매칭이면 {@code null}
 * @param placeName 선택한 카카오 장소명. 미매칭이면 {@code null}
 * @param matchConfidence 원본 가맹점명과 카카오 장소명의 이름 매칭 신뢰도. 0 이상 100 이하
 * @param longitude 선택 장소의 경도. 좌표를 확정하지 못하면 {@code null}
 * @param latitude 선택 장소의 위도. 좌표를 확정하지 못하면 {@code null}
 */
public record KakaoPlaceMatchData(
        String categoryCode,
        String placeId,
        String placeName,
        int matchConfidence,
        BigDecimal longitude,
        BigDecimal latitude
) {

    public static final String RESTAURANT_CATEGORY_CODE = "FD6";
    public static final String CAFE_CATEGORY_CODE = "CE7";
    public static final String UNKNOWN_CATEGORY_CODE = "UNKNOWN";

    private static final Set<String> ALLOWED_CATEGORY_CODES = Set.of(
            RESTAURANT_CATEGORY_CODE,
            CAFE_CATEGORY_CODE,
            UNKNOWN_CATEGORY_CODE
    );
    private static final int COORDINATE_SCALE = 5;
    private static final BigDecimal MIN_LONGITUDE = BigDecimal.valueOf(-180);
    private static final BigDecimal MAX_LONGITUDE = BigDecimal.valueOf(180);
    private static final BigDecimal MIN_LATITUDE = BigDecimal.valueOf(-90);
    private static final BigDecimal MAX_LATITUDE = BigDecimal.valueOf(90);

    /**
     * 카카오 매칭 결과의 카테고리·장소·신뢰도·좌표 조합을 검증하고 좌표 자릿수를 제한한다.
     */
    public KakaoPlaceMatchData {
        // 최종 파이프라인에서 허용하는 음식점·카페·미분류 코드인지 검증한다.
        requireText(categoryCode, "categoryCode");
        if (!ALLOWED_CATEGORY_CODES.contains(categoryCode)) {
            throw new IllegalArgumentException(
                    "categoryCode는 FD6, CE7, UNKNOWN 중 하나여야 합니다."
            );
        }

        // 선택 장소 ID와 이름이 회신됐다면 공백이 아닌지 검증한다.
        validateOptionalText(placeId, "placeId");
        validateOptionalText(placeName, "placeName");

        // 장소 ID와 이름은 같은 검색 결과이므로 함께 존재하거나 함께 누락되어야 한다.
        boolean hasPlaceId = placeId != null;
        boolean hasPlaceName = placeName != null;
        if (hasPlaceId != hasPlaceName) {
            throw new IllegalArgumentException(
                    "placeId와 placeName은 함께 존재하거나 함께 null이어야 합니다."
            );
        }

        // 이름 매칭 신뢰도가 백분율 범위 안에 있는지 검증한다.
        if (matchConfidence < 0 || matchConfidence > 100) {
            throw new IllegalArgumentException(
                    "matchConfidence는 0 이상 100 이하여야 합니다."
            );
        }

        // 미매칭과 매칭 상태가 장소 정보 존재 여부와 일치하는지 검증한다.
        boolean hasPlace = hasPlaceId;
        if (matchConfidence == 0 && hasPlace) {
            throw new IllegalArgumentException(
                    "matchConfidence가 0이면 장소 정보가 없어야 합니다."
            );
        }
        if (matchConfidence > 0 && !hasPlace) {
            throw new IllegalArgumentException(
                    "matchConfidence가 0보다 크면 장소 정보가 필요합니다."
            );
        }

        // 경도와 위도는 한 장소의 좌표이므로 둘의 존재 여부가 같아야 한다.
        if ((longitude == null) != (latitude == null)) {
            throw new IllegalArgumentException(
                    "longitude와 latitude는 함께 존재하거나 함께 null이어야 합니다."
            );
        }

        if (longitude != null) {
            // 좌표가 있으면 좌표를 제공한 카카오 장소 정보도 존재해야 한다.
            if (!hasPlace) {
                throw new IllegalArgumentException("좌표가 있으면 장소 정보도 필요합니다.");
            }

            // 절삭 전에 원본 경도·위도가 허용 범위 안에 있는지 검증한다.
            validateCoordinateRange(
                    longitude,
                    MIN_LONGITUDE,
                    MAX_LONGITUDE,
                    "longitude"
            );
            validateCoordinateRange(
                    latitude,
                    MIN_LATITUDE,
                    MAX_LATITUDE,
                    "latitude"
            );

            // 유효한 좌표는 값의 방향을 바꾸지 않고 소수점 다섯째 자리까지 절삭한다.
            longitude = truncateCoordinate(longitude);
            latitude = truncateCoordinate(latitude);
        }
    }

    /**
     * 검색 실패 또는 이름 미매칭 상태를 나타내는 기본 카카오 장소 결과를 만든다.
     *
     * @return 미분류 카테고리, 신뢰도 0, 장소·좌표 없음으로 구성된 결과
     */
    public static KakaoPlaceMatchData unmatched() {
        return new KakaoPlaceMatchData(
                UNKNOWN_CATEGORY_CODE,
                null,
                null,
                0,
                null,
                null
        );
    }

    /**
     * 필수 문자열이 존재하며 공백이 아닌지 확인한다.
     *
     * @param value 검사할 문자열
     * @param fieldName 오류 메시지에 표시할 필드명
     */
    private static void requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + "은(는) 필수입니다.");
        }
    }

    /**
     * 선택 문자열이 회신된 경우 공백 문자열이 아닌지 확인한다.
     *
     * @param value 검사할 선택 문자열
     * @param fieldName 오류 메시지에 표시할 필드명
     */
    private static void validateOptionalText(String value, String fieldName) {
        if (value != null && value.isBlank()) {
            throw new IllegalArgumentException(fieldName + "은(는) 공백일 수 없습니다.");
        }
    }

    /**
     * 좌표가 지정한 경도 또는 위도 범위 안에 있는지 확인한다.
     *
     * @param coordinate 검사할 좌표
     * @param minimum 허용 최솟값
     * @param maximum 허용 최댓값
     * @param fieldName 오류 메시지에 표시할 필드명
     */
    private static void validateCoordinateRange(
            BigDecimal coordinate,
            BigDecimal minimum,
            BigDecimal maximum,
            String fieldName
    ) {
        if (coordinate.compareTo(minimum) < 0 || coordinate.compareTo(maximum) > 0) {
            throw new IllegalArgumentException(
                    fieldName + "이(가) 허용 범위를 벗어났습니다."
            );
        }
    }

    /**
     * 좌표를 0 방향으로 절삭해 소수점 다섯 자리 값으로 만든다.
     *
     * @param coordinate 범위 검증을 통과한 좌표
     * @return 소수점 다섯째 자리까지 보존한 좌표
     */
    private static BigDecimal truncateCoordinate(BigDecimal coordinate) {
        return coordinate.setScale(COORDINATE_SCALE, RoundingMode.DOWN);
    }
}
