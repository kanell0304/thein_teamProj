package com.anything.momeogji.mydata.transform.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 옵션 계층에서 선택한 시각에 따라 필터링된 동일 가맹점의 결제와 카카오 장소 매칭 결과를 묶은 최종 데이터다.
 *
 * <p>원본 가맹점명은 카카오 로컬 검색에 사용하기 위해 변경하지 않고 보존한다.
 * 승인번호는 정확한 중복 제거가 끝난 1차 정제 이후 폐기하고, 승인시각과 승인금액은
 * 서로의 대응 관계가 어긋나지 않도록 {@link PaymentLog} 목록으로 함께 보존한다.</p>
 *
 * <p>가맹점 집계 직후 생성하는 데이터는 {@link KakaoPlaceMatchData#unmatched()}를 사용한다.
 * 이후 장소 분류 단계는
 * {@link #withKakaoPlaceMatch(KakaoPlaceMatchData)}로 카카오 결과만 교체한 새 값을 만든다.</p>
 *
 * @param merchantName 최초 결제에서 보존한 원본 가맹점명. 미회신이면 {@code null}
 * @param merchantCode 가맹점을 구분하는 사업자등록번호 기반 코드. 미회신이면 {@code null}
 * @param payments 원본 등장 순서를 유지한 승인시각·승인금액 쌍의 불변 목록
 * @param kakaoPlaceMatch 카카오 장소 검색의 매칭 결과. 미매칭 상태도 null 대신 전용 값으로 표현
 */
public record MerchantUsageData(
        String merchantName,
        String merchantCode,
        List<PaymentLog> payments,
        KakaoPlaceMatchData kakaoPlaceMatch
) {

    /**
     * 가맹점 사용 이력과 카카오 장소 결과가 후속 처리에 사용할 수 있는 상태인지 검증한다.
     * 원본 문자열과 결제 발생 순서는 변경하지 않으며 목록만 방어적으로 복사한다.
     */
    public MerchantUsageData {
        // 선택적으로 회신된 가맹점 문자열은 값이 있다면 공백이 아닌지 검증한다.
        validateOptionalText(merchantName, "merchantName");
        validateOptionalText(merchantCode, "merchantCode");

        // 가맹점명과 가맹점 코드가 모두 누락된 집계 결과는 생성하지 못하게 검증한다.
        if (merchantName == null && merchantCode == null) {
            throw new IllegalArgumentException(
                    "merchantName과 merchantCode 중 하나는 필수입니다."
            );
        }

        // 집계 결과에는 한 건 이상의 결제 로그가 존재하는지 검증한다.
        if (payments == null) {
            throw new IllegalArgumentException("payments는 null일 수 없습니다.");
        }
        if (payments.isEmpty()) {
            throw new IllegalArgumentException("payments는 한 건 이상이어야 합니다.");
        }

        // 모든 결제 로그가 존재하는지 검증한다.
        for (int index = 0; index < payments.size(); index++) {
            PaymentLog payment = payments.get(index);
            if (payment == null) {
                throw new IllegalArgumentException(
                        "payments[" + index + "]은 null일 수 없습니다."
                );
            }
        }

        // 호출자가 전달한 목록을 변경해도 가맹점 사용 이력이 바뀌지 않도록 불변 목록으로 복사한다.
        payments = List.copyOf(payments);

        // 분류 전·후 상태를 null로 구분하지 않도록 카카오 장소 결과를 필수로 검증한다.
        if (kakaoPlaceMatch == null) {
            throw new IllegalArgumentException("kakaoPlaceMatch는 필수입니다.");
        }
    }

    /**
     * 집계가 끝났지만 카카오 장소 검색은 아직 반영하지 않은 가맹점 사용 이력을 만든다.
     *
     * @param merchantName 최초 결제의 원본 가맹점명
     * @param merchantCode 사업자등록번호 기반 가맹점 코드
     * @param payments 승인시각·승인금액 결제 로그 목록
     * @return 미매칭 카카오 결과를 포함한 가맹점 사용 이력
     */
    public static MerchantUsageData unclassified(
            String merchantName,
            String merchantCode,
            List<PaymentLog> payments
    ) {
        // 검색 전에는 장소를 찾지 못한 기본 결과를 명시적으로 저장한다.
        return new MerchantUsageData(
                merchantName,
                merchantCode,
                payments,
                KakaoPlaceMatchData.unmatched()
        );
    }

    /**
     * 기본 가맹점·결제 정보는 유지하고 카카오 장소 매칭 결과만 교체한 새 값을 만든다.
     *
     * @param kakaoPlaceMatch 카카오 검색과 이름 비교가 끝난 최종 장소 매칭 결과
     * @return 기존 사용 이력과 새 카카오 장소 결과를 결합한 불변 데이터
     */
    public MerchantUsageData withKakaoPlaceMatch(
            KakaoPlaceMatchData kakaoPlaceMatch
    ) {
        // record를 변경하지 않고 동일한 사용 이력에 새 장소 결과를 적용한 객체를 생성한다.
        return new MerchantUsageData(
                merchantName,
                merchantCode,
                payments,
                kakaoPlaceMatch
        );
    }

    /**
     * 선택 문자열이 회신된 경우 공백 문자열이 아닌지 확인한다.
     *
     * @param value 검사할 선택 문자열
     * @param fieldName 오류 메시지에 표시할 필드명
     * @throws IllegalArgumentException 값이 공백 문자열인 경우
     */
    private static void validateOptionalText(String value, String fieldName) {
        if (value != null && value.isBlank()) {
            throw new IllegalArgumentException(fieldName + "은(는) 공백일 수 없습니다.");
        }
    }

    /**
     * 한 번의 결제에서 서로 짝을 이루는 승인시각과 승인금액을 보존하는 불변 로그다.
     *
     * @param approvedAt 최초 승인일시
     * @param approvedAmount 최초 승인금액
     */
    public record PaymentLog(
            LocalDateTime approvedAt,
            BigDecimal approvedAmount
    ) {

        /**
         * 결제 로그가 시간대 분류와 금액 통계에 사용할 수 있는 값인지 검증한다.
         */
        public PaymentLog {
            // 승인시각이 존재하는지 검증한다.
            if (approvedAt == null) {
                throw new IllegalArgumentException("approvedAt은 필수입니다.");
            }

            // 승인금액이 존재하며 음수가 아닌지 검증한다.
            if (approvedAmount == null) {
                throw new IllegalArgumentException("approvedAmount는 필수입니다.");
            }
            if (approvedAmount.signum() < 0) {
                throw new IllegalArgumentException("approvedAmount는 0 이상이어야 합니다.");
            }
        }
    }
}
