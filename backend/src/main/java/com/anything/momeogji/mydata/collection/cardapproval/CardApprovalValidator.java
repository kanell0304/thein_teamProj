package com.anything.momeogji.mydata.collection.cardapproval;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.List;
import java.util.Set;

/**
 * 카드별 국내 승인내역 응답의 필수값과 데이터 형식을 검증
 *
 * 공통 필수값과 코드 범위뿐 아니라 승인취소·정정 상태에 따라 달라지는 조건부 필드도 검사
 * 응답 정렬과 요청 기간 포함 여부는 이 클래스의 검증 범위에 포함하지 않는다.
 */
@Component
public class CardApprovalValidator {

    private static final String SUCCESS_RESPONSE_CODE = "00000";
    private static final Set<String> STATUS_CODES = Set.of("01", "02", "03", "04");
    private static final Set<String> PAY_TYPE_CODES = Set.of("01", "02");
    private static final Set<String> TRANSACTION_TIME_REQUIRED_STATUSES = Set.of("02", "03");
    private static final String CORRECTION_STATUS_CODE = "03";
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter
            .ofPattern("uuuuMMddHHmmss")
            .withResolverStyle(ResolverStyle.STRICT);

    /**
     * 국내 승인내역 응답의 공통 필드와 승인 항목별 상태 규칙을 검증한다.
     *
     * @param response ObjectMapper로 역직렬화한 국내 승인내역 응답
     * @throws IllegalArgumentException 응답 코드 또는 승인내역 필드 규칙이 올바르지 않은 경우
     */
    public void validate(CardApprovalResponse response) {
        // ObjectMapper 역직렬화 결과인 국내 승인내역 응답 객체의 누락 여부를 검사한다.
        if (response == null) {
            throw new IllegalArgumentException("국내 승인내역 응답은 null일 수 없습니다.");
        }

        // 후속 파싱이 가능한 정상 응답 코드인 00000인지 검사한다.
        if (!SUCCESS_RESPONSE_CODE.equals(response.responseCode())) {
            throw new IllegalArgumentException(
                    "국내 승인내역 조회가 실패했습니다. rsp_code=" + response.responseCode()
            );
        }

        // 세부 응답메시지 rsp_msg가 비어 있지 않은지 검사한다.
        validateRequiredText(response.responseMessage(), "rsp_msg");

        // 국내 승인내역수 approved_cnt가 누락되지 않았고 0 이상의 정수인지 검사한다.
        Integer approvalCount = response.approvalCount();
        if (approvalCount == null || approvalCount < 0) {
            throw new IllegalArgumentException("approved_cnt는 0 이상의 정수여야 합니다.");
        }

        // 국내 승인내역목록 approved_list 자체가 누락되지 않았는지 검사한다.
        List<CardApprovalResponse.ApprovalItem> approvals = response.approvals();
        if (approvals == null) {
            throw new IllegalArgumentException("approved_list는 null일 수 없습니다.");
        }

        // 응답에 선언된 approved_cnt와 실제 approved_list 항목 수가 일치하는지 검사한다.
        if (approvalCount != approvals.size()) {
            throw new IllegalArgumentException(
                    "approved_cnt와 approved_list 크기가 일치하지 않습니다. approved_cnt="
                            + approvalCount + ", 실제 크기=" + approvals.size()
            );
        }

        // 모든 승인내역 항목을 원본 순서대로 검사한다.
        for (int index = 0; index < approvals.size(); index++) {
            validateApproval(approvals.get(index), index);
        }
    }

    /**
     * 단일 승인내역의 공통 필수값과 결제상태별 조건부 필드를 검증
     *
     * @param approval 검사할 승인내역 항목
     * @param index 오류 메시지에 표시할 승인내역 목록 위치
     * @throws IllegalArgumentException 승인내역이 없거나 필드 규칙이 올바르지 않은 경우
     */
    private void validateApproval(CardApprovalResponse.ApprovalItem approval, int index) {
        String fieldPrefix = "approved_list[" + index + "]";

        // approved_list 안의 개별 승인내역 항목이 null인지 검사한다.
        if (approval == null) {
            throw new IllegalArgumentException(fieldPrefix + "는 null일 수 없습니다.");
        }

        // 카드사가 발행한 승인번호 approved_num이 비어 있지 않은지 검사한다.
        validateRequiredText(approval.approvalNumber(), fieldPrefix + ".approved_num");

        // 승인일시 approved_dtime이 필수이며 실제 존재하는 14자리 시각인지 검사한다.
        validateRequiredDateTime(approval.approvedDateTime(), fieldPrefix + ".approved_dtime");

        // 결제상태 status가 필수이며 정의된 01·02·03·04 코드인지 검사한다.
        validateRequiredText(approval.statusCode(), fieldPrefix + ".status");
        if (!STATUS_CODES.contains(approval.statusCode())) {
            throw new IllegalArgumentException(
                    fieldPrefix + ".status는 01, 02, 03, 04 중 하나여야 합니다: "
                            + approval.statusCode()
            );
        }

        // 사용구분 pay_type이 필수이며 신용 01 또는 체크 02 코드인지 검사한다.
        validateRequiredText(approval.payTypeCode(), fieldPrefix + ".pay_type");
        if (!PAY_TYPE_CODES.contains(approval.payTypeCode())) {
            throw new IllegalArgumentException(
                    fieldPrefix + ".pay_type은 01 또는 02여야 합니다: " + approval.payTypeCode()
            );
        }

        // 이용금액 approved_amt가 누락되지 않았고 0 이상인지 검사한다.
        validateRequiredAmount(
                approval.approvedAmount(),
                fieldPrefix + ".approved_amt"
        );

        // 승인취소 02 또는 정정 03이면 trans_dtime이 필수이고 유효한 시각인지 검사한다.
        if (TRANSACTION_TIME_REQUIRED_STATUSES.contains(approval.statusCode())) {
            validateRequiredDateTime(
                    approval.transactionDateTime(),
                    fieldPrefix + ".trans_dtime"
            );
        } else {
            // 다른 상태에서도 trans_dtime이 회신됐다면 빈 값이나 잘못된 시각을 허용하지 않는다.
            validateOptionalDateTime(
                    approval.transactionDateTime(),
                    fieldPrefix + ".trans_dtime"
            );
        }

        // 정정 상태 03이면 modified_amt가 필수이고 0 이상인지 검사한다.
        if (CORRECTION_STATUS_CODE.equals(approval.statusCode())) {
            validateRequiredAmount(
                    approval.modifiedAmount(),
                    fieldPrefix + ".modified_amt"
            );
        } else {
            // 정정 이외 상태에서도 modified_amt가 회신됐다면 음수 값을 허용하지 않는다.
            validateOptionalAmount(
                    approval.modifiedAmount(),
                    fieldPrefix + ".modified_amt"
            );
        }

        // 전체 할부회차 total_install_cnt가 회신됐다면 1 이상인지 검사한다.
        if (approval.totalInstallmentCount() != null && approval.totalInstallmentCount() < 1) {
            throw new IllegalArgumentException(
                    fieldPrefix + ".total_install_cnt는 1 이상이어야 합니다."
            );
        }

        // 선택 필드인 가맹점명이 회신됐다면 빈 문자열이 아닌지 검사한다.
        validateOptionalText(approval.merchantName(), fieldPrefix + ".merchant_name");

        // 선택 필드인 가맹점 사업자등록번호가 회신됐다면 빈 문자열이 아닌지 검사한다.
        validateOptionalText(
                approval.merchantRegistrationNumber(),
                fieldPrefix + ".merchant_regno"
        );
    }

    /**
     * 필수 문자열이 {@code null}, 빈 문자열 또는 공백 문자열이 아닌지 확인한다.
     *
     * @param value 검사할 문자열 값
     * @param fieldName 오류 메시지에 표시할 API 필드 경로
     * @throws IllegalArgumentException 값이 없거나 공백으로만 구성된 경우
     */
    private void validateRequiredText(String value, String fieldName) {
        // API 명세상 필수 문자열이 누락됐거나 공백으로만 구성됐는지 검사한다.
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + "은(는) 필수입니다.");
        }
    }

    /**
     * 선택 문자열이 회신된 경우 빈 문자열 또는 공백 문자열이 아닌지 확인한다.
     *
     * @param value 검사할 선택 문자열 값
     * @param fieldName 오류 메시지에 표시할 API 필드 경로
     * @throws IllegalArgumentException 회신된 값이 빈 문자열이거나 공백으로만 구성된 경우
     */
    private void validateOptionalText(String value, String fieldName) {
        // 선택 필드는 미회신 null을 허용하지만, 회신된 빈 값은 허용하지 않는다.
        if (value != null && value.isBlank()) {
            throw new IllegalArgumentException(fieldName + "은(는) 회신 시 비어 있을 수 없습니다.");
        }
    }

    /**
     * 필수 일시 문자열이 존재하고 실제 {@code yyyyMMddHHmmss} 시각인지 확인한다.
     *
     * @param value 검사할 일시 문자열
     * @param fieldName 오류 메시지에 표시할 API 필드 경로
     * @throws IllegalArgumentException 값이 없거나 실제 존재하지 않는 시각인 경우
     */
    private void validateRequiredDateTime(String value, String fieldName) {
        // 필수 일시 문자열이 누락됐거나 공백인지 먼저 검사한다.
        validateRequiredText(value, fieldName);

        // 14자리 일시 문자열이 달력상 실제 존재하는 시각인지 엄격하게 검사한다.
        validateDateTimeFormat(value, fieldName);
    }

    /**
     * 선택 일시 문자열이 회신된 경우 실제 {@code yyyyMMddHHmmss} 시각인지 확인한다.
     *
     * @param value 검사할 선택 일시 문자열
     * @param fieldName 오류 메시지에 표시할 API 필드 경로
     * @throws IllegalArgumentException 회신된 값이 비어 있거나 실제 존재하지 않는 시각인 경우
     */
    private void validateOptionalDateTime(String value, String fieldName) {
        // 선택 일시 필드는 미회신 null을 허용한다.
        if (value == null) {
            return;
        }

        // 회신된 선택 일시가 빈 문자열 또는 공백인지 검사한다.
        validateRequiredText(value, fieldName);

        // 회신된 일시 문자열이 달력상 실제 존재하는 시각인지 엄격하게 검사한다.
        validateDateTimeFormat(value, fieldName);
    }

    /**
     * 일시 문자열을 엄격한 형식으로 파싱해 형식과 달력값을 동시에 확인한다.
     *
     * @param value 검사할 일시 문자열
     * @param fieldName 오류 메시지에 표시할 API 필드 경로
     * @throws IllegalArgumentException 형식이 다르거나 실제 존재하지 않는 시각인 경우
     */
    private void validateDateTimeFormat(String value, String fieldName) {
        try {
            // ResolverStyle.STRICT를 적용해 잘못된 월·일·시·분·초를 거부한다.
            LocalDateTime.parse(value, DATE_TIME_FORMATTER);
        } catch (DateTimeParseException exception) {
            throw new IllegalArgumentException(
                    fieldName + "은(는) 실제 존재하는 yyyyMMddHHmmss 시각이어야 합니다: " + value,
                    exception
            );
        }
    }

    /**
     * 필수 금액이 존재하고 0 이상인지 확인한다.
     *
     * @param amount 검사할 금액
     * @param fieldName 오류 메시지에 표시할 API 필드 경로
     * @throws IllegalArgumentException 금액이 없거나 음수인 경우
     */
    private void validateRequiredAmount(BigDecimal amount, String fieldName) {
        // API 명세상 필수 금액이 누락됐는지 검사한다.
        if (amount == null) {
            throw new IllegalArgumentException(fieldName + "은(는) 필수입니다.");
        }

        // 필수 금액이 0보다 작은 음수인지 검사한다.
        validateOptionalAmount(amount, fieldName);
    }

    /**
     * 선택 금액이 회신된 경우 0 이상인지 확인한다.
     *
     * @param amount 검사할 선택 금액
     * @param fieldName 오류 메시지에 표시할 API 필드 경로
     * @throws IllegalArgumentException 회신된 금액이 음수인 경우
     */
    private void validateOptionalAmount(BigDecimal amount, String fieldName) {
        // 선택 금액은 미회신 null을 허용하지만 음수 값은 허용하지 않는다.
        if (amount != null && amount.signum() < 0) {
            throw new IllegalArgumentException(fieldName + "은(는) 0 이상이어야 합니다.");
        }
    }
}
