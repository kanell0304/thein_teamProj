package com.anything.momeogji.mydata.collection.cardapproval;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.util.List;

/**
 * 카드별 국내 승인내역 조회 v2의 Raw JSON 응답을 Java 타입으로 역직렬화하는 DTO
 *
 * 일시는 검증 전 원문을 유지하기 위해 문자열로 받고, 금액은 소수점 손실을 방지하기 위해 {@link BigDecimal}로 받는다.
 * 승인내역 검증과 내부 모델 변환은 각각 {@link CardApprovalValidator}와 {@link CardApprovalParser}가 담당
 *
 * @param responseCode 세부 응답 코드
 * @param responseMessage 세부 응답 메시지
 * @param nextPage 다음 페이지 요청에 사용할 기준개체. 마지막 페이지이면 {@code null}
 * @param approvalCount 국내 승인내역 개수
 * @param approvals 국내 승인내역 목록
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record CardApprovalResponse(
        @JsonProperty("rsp_code") String responseCode,
        @JsonProperty("rsp_msg") String responseMessage,
        @JsonProperty("next_page") String nextPage,
        @JsonProperty("approved_cnt") Integer approvalCount,
        @JsonProperty("approved_list") List<ApprovalItem> approvals
) {

    /**
     * 국내 승인내역 목록의 한 항목을 API 필드에 대응해 표현
     *
     * @param approvalNumber 카드사가 발행한 승인번호
     * @param approvedDateTime 승인일시 원문
     * @param statusCode 결제상태 코드
     * @param payTypeCode 신용·체크 사용구분 코드
     * @param transactionDateTime 정정 또는 승인취소 일시 원문
     * @param merchantName 가맹점명
     * @param merchantRegistrationNumber 가맹점 사업자등록번호
     * @param approvedAmount 이용금액
     * @param modifiedAmount 정정 후 이용금액
     * @param totalInstallmentCount 전체 할부회차
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ApprovalItem(
            @JsonProperty("approved_num") String approvalNumber,
            @JsonProperty("approved_dtime") String approvedDateTime,
            @JsonProperty("status") String statusCode,
            @JsonProperty("pay_type") String payTypeCode,
            @JsonProperty("trans_dtime") String transactionDateTime,
            @JsonProperty("merchant_name") String merchantName,
            @JsonProperty("merchant_regno") String merchantRegistrationNumber,
            @JsonProperty("approved_amt") BigDecimal approvedAmount,
            @JsonProperty("modified_amt") BigDecimal modifiedAmount,
            @JsonProperty("total_install_cnt") Integer totalInstallmentCount
    ) {
    }
}
