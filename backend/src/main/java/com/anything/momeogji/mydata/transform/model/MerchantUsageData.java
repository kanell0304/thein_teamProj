package com.anything.momeogji.mydata.transform.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 선택한 시간대에 발생한 동일 가맹점의 결제들을 하나로 묶은 데이터
 *
 * 원본 가맹점명은 카카오 로컬 검색에 사용하기 위해 변경하지 않고 보존한다.
 * 승인번호는 정확한 중복 제거가 끝난 1차 정제 이후 폐기
 * 승인시각과 승인금액은 서로의 대응 관계가 어긋나지 않도록 {@link PaymentOccurrence} 목록으로 함께 보존한다.</p>
 *
 * @param merchantName 최초 결제에서 보존한 원본 가맹점명. 미회신이면 {@code null}
 * @param merchantCode 가맹점을 구분하는 사업자등록번호 기반 코드. 미회신이면 {@code null}
 * @param timeBand 집계에 포함된 모든 결제가 속한 시간대
 * @param payments 원본 등장 순서를 유지한 승인시각·승인금액 쌍의 불변 목록
 */
public record MerchantUsageData(
        String merchantName,
        String merchantCode,
        TimeBand timeBand,
        List<PaymentOccurrence> payments
) {

    /**
     * 가맹점 집계 결과가 후속 로컬 검색과 통계 계산에 사용할 수 있는 상태인지 검증한다.
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

        // 집계 기준 시간대가 존재하는지 검증한다.
        if (timeBand == null) {
            throw new IllegalArgumentException("timeBand는 필수입니다.");
        }

        // 집계 결과에는 한 건 이상의 결제 발생 정보가 존재하는지 검증한다.
        if (payments == null) {
            throw new IllegalArgumentException("payments는 null일 수 없습니다.");
        }
        if (payments.isEmpty()) {
            throw new IllegalArgumentException("payments는 한 건 이상이어야 합니다.");
        }

        // 모든 결제 발생 정보가 존재하며 집계 시간대와 일치하는지 검증한다.
        for (int index = 0; index < payments.size(); index++) {
            PaymentOccurrence occurrence = payments.get(index);
            if (occurrence == null) {
                throw new IllegalArgumentException(
                        "payments[" + index + "]은 null일 수 없습니다."
                );
            }
            if (TimeBand.from(occurrence.approvedAt()) != timeBand) {
                throw new IllegalArgumentException(
                        "payments[" + index + "]의 승인시각이 timeBand와 일치하지 않습니다."
                );
            }
        }

        // 호출자가 전달한 목록을 변경해도 집계 결과가 바뀌지 않도록 불변 목록으로 복사한다.
        payments = List.copyOf(payments);
    }

    /**
     * 동일 가맹점·시간대에 포함된 실제 결제 횟수를 반환한다.
     *
     * @return 결제 발생 목록의 크기
     */
    public int paymentCount() {
        return payments.size();
    }

    /**
     * 집계된 결제 중 가장 최근 승인일시를 계산한다.
     *
     * @return 결제 발생 목록에서 가장 늦은 승인일시
     */
    public LocalDateTime latestVisitedAt() {
        LocalDateTime latest = payments.get(0).approvedAt();

        // 원본 순서와 관계없이 가장 늦은 승인일시를 최근 방문일로 선택한다.
        for (int index = 1; index < payments.size(); index++) {
            LocalDateTime candidate = payments.get(index).approvedAt();
            if (candidate.isAfter(latest)) {
                latest = candidate;
            }
        }
        return latest;
    }

    /**
     * 동일 가맹점·시간대에 포함된 승인금액의 합계를 계산한다.
     *
     * @return 모든 결제 발생 금액의 합계
     */
    public BigDecimal totalApprovedAmount() {
        BigDecimal total = BigDecimal.ZERO;

        // 각 결제 발생의 승인금액을 누적하여 총 결제금액을 계산한다.
        for (PaymentOccurrence occurrence : payments) {
            total = total.add(occurrence.approvedAmount());
        }
        return total;
    }

    /**
     * 동일 가맹점·시간대의 평균 승인금액을 소수점 둘째 자리까지 계산한다.
     * 나누어떨어지지 않는 값은 일반적인 반올림 방식인 {@link RoundingMode#HALF_UP}을 사용한다.
     *
     * @return 총 승인금액을 방문 횟수로 나눈 평균 승인금액
     */
    public BigDecimal averageApprovedAmount() {
        // 한 건 이상임이 생성자에서 보장된 목록의 크기로 총 결제금액을 나눈다.
        return totalApprovedAmount().divide(
                BigDecimal.valueOf(paymentCount()),
                2,
                RoundingMode.HALF_UP
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
     * 한 번의 결제에서 서로 짝을 이루는 승인시각과 승인금액을 보존하는 불변 값이다.
     *
     * @param approvedAt 최초 승인일시
     * @param approvedAmount 최초 승인금액
     */
    public record PaymentOccurrence(
            LocalDateTime approvedAt,
            BigDecimal approvedAmount
    ) {

        /**
         * 결제 발생 정보가 시간대 분류와 금액 통계에 사용할 수 있는 값인지 검증한다.
         */
        public PaymentOccurrence {
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
