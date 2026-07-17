package com.anything.momeogji.mydata.transform.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 카드 승인내역의 상태와 가맹점 정보 누락 여부를 확인한 뒤 남기는 데이터
 *
 * 원본 내부 모델과 달리 카드 식별자, 상태 코드, 결제수단 코드, 취소·정정 정보와 할부 정보는 포함하지 않는다.
 * 정확한 결제 중복을 판별하는 데 필요한 승인번호와 승인시각, 가맹점 정보, 승인금액은 원본 값을 정규화하지 않고 그대로 보존한다
 *
 * @param approvalNumber 카드사가 발행한 승인번호
 * @param approvedAt 최초 승인일시
 * @param merchantName 가맹점명. 미회신이면 {@code null}
 * @param merchantRegistrationNumber 가맹점 사업자등록번호. 미회신이면 {@code null}
 * @param approvedAmount 최초 승인금액
 */
public record CleanApprovalData(
        String approvalNumber,
        LocalDateTime approvedAt,
        String merchantName,
        String merchantRegistrationNumber,
        BigDecimal approvedAmount
) {

    /**
     * 1차 정제 결과가 후속 중복 제거와 가맹점 분류에 사용할 수 있는 값인지 검증한다.
     * 입력 문자열은 검증만 수행하며 자르거나 다른 값으로 정규화하지 않는다.
     */
    public CleanApprovalData {
        // 승인 식별값과 승인시각이 반드시 존재하는지 검증한다.
        requireText(approvalNumber, "approvalNumber");
        if (approvedAt == null) {
            throw new IllegalArgumentException("approvedAt은 필수입니다.");
        }

        // 선택적으로 회신되는 가맹점 값은 존재할 경우 공백이 아닌지 검증한다.
        validateOptionalText(merchantName, "merchantName");
        validateOptionalText(merchantRegistrationNumber, "merchantRegistrationNumber");

        // 가맹점명과 사업자등록번호가 모두 누락된 결과는 생성하지 못하게 검증한다.
        if (merchantName == null && merchantRegistrationNumber == null) {
            throw new IllegalArgumentException(
                    "merchantName과 merchantRegistrationNumber 중 하나는 필수입니다."
            );
        }

        // 승인금액이 존재하며 음수가 아닌지 검증한다.
        if (approvedAmount == null) {
            throw new IllegalArgumentException("approvedAmount는 필수입니다.");
        }
        if (approvedAmount.signum() < 0) {
            throw new IllegalArgumentException("approvedAmount는 0 이상이어야 합니다.");
        }
    }

    /**
     * 필수 문자열이 존재하며 공백이 아닌지 확인한다.
     *
     * @param value 검사할 문자열
     * @param fieldName 오류 메시지에 표시할 필드명
     * @throws IllegalArgumentException 값이 없거나 공백인 경우
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
     * @throws IllegalArgumentException 값이 공백 문자열인 경우
     */
    private static void validateOptionalText(String value, String fieldName) {
        if (value != null && value.isBlank()) {
            throw new IllegalArgumentException(fieldName + "은(는) 공백일 수 없습니다.");
        }
    }
}
