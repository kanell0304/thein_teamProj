package com.anything.momeogji.mydata.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * 카드별 국내 승인내역 응답을 검증·파싱한 뒤 내부 처리에 사용하는 한 건의 데이터다.
 *
 * <p>Raw API의 문자열 날짜와 숫자 금액을 각각 {@link LocalDateTime}과
 * {@link BigDecimal}로 변환한다. 상태 코드와 사용구분 코드는 원본 추적과
 * 취소·정정 규칙 적용을 위해 코드값을 유지한다.</p>
 *
 * @param cardId 카드 목록 응답에서 받은 카드 고유 식별자
 * @param approvalNumber 카드사가 발행한 승인번호
 * @param approvedAt 최초 승인일시
 * @param statusCode 결제상태 코드: 01 승인, 02 승인취소, 03 정정, 04 무승인 매입
 * @param payTypeCode 사용구분 코드: 01 신용, 02 체크
 * @param transactionAt 취소 또는 정정 발생일시. 해당하지 않으면 {@code null}
 * @param merchantName 가맹점명. 별도 제공 동의가 없거나 미회신이면 {@code null}
 * @param merchantRegistrationNumber 가맹점 사업자등록번호. 미회신이면 {@code null}
 * @param approvedAmount 최초 승인금액
 * @param modifiedAmount 정정 후 금액. 정정 상태가 아니면 {@code null}
 * @param totalInstallmentCount 전체 할부회차. 일시불이면 {@code null}
 */
public record CardApprovalData(
        String cardId,
        String approvalNumber,
        LocalDateTime approvedAt,
        String statusCode,
        String payTypeCode,
        LocalDateTime transactionAt,
        String merchantName,
        String merchantRegistrationNumber,
        BigDecimal approvedAmount,
        BigDecimal modifiedAmount,
        Integer totalInstallmentCount
) {

    /**
     * 정제 결과가 반드시 가져야 하는 공통 식별값과 금액을 방어적으로 확인한다.
     * 상태별 조건 검증은 CardApprovalValidator가 담당하므로 여기에서 중복하지 않는다.
     */
    public CardApprovalData {
        requireText(cardId, "cardId");
        requireText(approvalNumber, "approvalNumber");
        Objects.requireNonNull(approvedAt, "approvedAt은 필수입니다.");
        requireText(statusCode, "statusCode");
        requireText(payTypeCode, "payTypeCode");
        Objects.requireNonNull(approvedAmount, "approvedAmount는 필수입니다.");

        if (totalInstallmentCount != null && totalInstallmentCount < 1) {
            throw new IllegalArgumentException("totalInstallmentCount는 1 이상이어야 합니다.");
        }
    }

    private static void requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + "은(는) 필수입니다.");
        }
    }
}
