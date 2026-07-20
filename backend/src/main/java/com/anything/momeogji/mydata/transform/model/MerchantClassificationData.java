package com.anything.momeogji.mydata.transform.model;

import java.util.Set;

/**
 * 가맹점별 결제 사용 이력에 카카오 로컬 검색으로 확인한 카테고리와 장소 정보를 결합한 불변 데이터
 *
 * 카카오 검색에 실패하거나 카테고리 그룹을 확인하지 못한 가맹점도 {@link #UNKNOWN_CATEGORY_CODE}로 보존한다.
 * 좌표는 한 장소를 명확히 선택하고 경도·위도 값을 모두 검증한 경우에만 함께 저장한다.
 *
 * @param merchantUsage 카카오 검색 전 단계에서 생성한 가맹점별 결제 사용 이력
 * @param categoryCode 음식점 {@code FD6}, 카페 {@code CE7}, 내부 미분류값 {@code UNKNOWN} 중 하나
 * @param matchConfidence 원본 가맹점명과 카카오 장소명의 매칭 신뢰도. 0 이상 100 이하
 * @param matchedPlaceId 선택한 카카오 장소 ID. 매칭하지 못했으면 {@code null}
 * @param matchedPlaceName 선택한 카카오 장소명. 매칭하지 못했으면 {@code null}
 * @param x 선택한 장소의 경도. 좌표를 확정하지 못했으면 {@code null}
 * @param y 선택한 장소의 위도. 좌표를 확정하지 못했으면 {@code null}
 */
public record MerchantClassificationData(
        MerchantUsageData merchantUsage,
        String categoryCode,
        int matchConfidence,
        String matchedPlaceId,
        String matchedPlaceName,
        Double x,
        Double y
) {

    public static final String RESTAURANT_CATEGORY_CODE = "FD6";
    public static final String CAFE_CATEGORY_CODE = "CE7";
    public static final String UNKNOWN_CATEGORY_CODE = "UNKNOWN";

    private static final Set<String> ALLOWED_CATEGORY_CODES = Set.of(
            RESTAURANT_CATEGORY_CODE,
            CAFE_CATEGORY_CODE,
            UNKNOWN_CATEGORY_CODE
    );

    /**
     * 분류 결과가 후속 AI 입력으로 안전하게 전달될 수 있도록 필수값과 필드 조합을 검증한다.
     */
    public MerchantClassificationData {
        // 원본 가맹점 사용 이력이 존재하는지 검증한다.
        if (merchantUsage == null) {
            throw new IllegalArgumentException("merchantUsage는 필수입니다.");
        }

        // 최종 파이프라인에서 허용하는 음식점·카페·미분류 코드인지 검증한다.
        requireText(categoryCode, "categoryCode");
        if (!ALLOWED_CATEGORY_CODES.contains(categoryCode)) {
            throw new IllegalArgumentException(
                    "categoryCode는 FD6, CE7, UNKNOWN 중 하나여야 합니다."
            );
        }

        // 가맹점명 매칭 신뢰도가 백분율 범위 안에 있는지 검증한다.
        if (matchConfidence < 0 || matchConfidence > 100) {
            throw new IllegalArgumentException(
                    "matchConfidence는 0 이상 100 이하여야 합니다."
            );
        }

        // 장소 ID와 장소명은 하나의 매칭 결과이므로 둘의 존재 여부가 같은지 검증한다.
        validateOptionalText(matchedPlaceId, "matchedPlaceId");
        validateOptionalText(matchedPlaceName, "matchedPlaceName");
        boolean hasMatchedPlaceId = matchedPlaceId != null;
        boolean hasMatchedPlaceName = matchedPlaceName != null;
        if (hasMatchedPlaceId != hasMatchedPlaceName) {
            throw new IllegalArgumentException(
                    "matchedPlaceId와 matchedPlaceName은 함께 존재하거나 함께 null이어야 합니다."
            );
        }

        // 매칭 실패와 성공 상태가 장소 정보 존재 여부와 일치하는지 검증한다.
        boolean hasMatchedPlace = hasMatchedPlaceId;
        if (matchConfidence == 0 && hasMatchedPlace) {
            throw new IllegalArgumentException(
                    "matchConfidence가 0이면 매칭 장소 정보가 없어야 합니다."
            );
        }
        if (matchConfidence > 0 && !hasMatchedPlace) {
            throw new IllegalArgumentException(
                    "matchConfidence가 0보다 크면 매칭 장소 정보가 필요합니다."
            );
        }

        // 좌표는 경도와 위도가 모두 있을 때만 의미가 있으므로 한 쪽만 저장하지 못하게 검증한다.
        if ((x == null) != (y == null)) {
            throw new IllegalArgumentException("x와 y는 함께 존재하거나 함께 null이어야 합니다.");
        }

        // 회신된 좌표가 유한한 WGS84 경도·위도 범위 안에 있는지 검증한다.
        if (x != null) {
            if (!Double.isFinite(x) || x < -180.0 || x > 180.0) {
                throw new IllegalArgumentException("x는 -180 이상 180 이하의 유한한 값이어야 합니다.");
            }
            if (!Double.isFinite(y) || y < -90.0 || y > 90.0) {
                throw new IllegalArgumentException("y는 -90 이상 90 이하의 유한한 값이어야 합니다.");
            }
            if (!hasMatchedPlace) {
                throw new IllegalArgumentException("좌표가 있으면 매칭 장소 정보도 필요합니다.");
            }
        }
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
}
