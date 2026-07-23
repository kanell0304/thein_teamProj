package com.anything.momeogji.mydata.collection.cardapproval;

import com.anything.momeogji.mydata.collection.model.CardApprovalData;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.ResolverStyle;
import java.util.List;

/**
 * 검증이 끝난 국내 승인내역 응답을 내부 처리용 {@link CardApprovalData} 목록으로 변환
 *
 * 카드 목록에서 전달받은 카드 ID를 각 승인내역에 결합하고, Raw 일시 문자열을{@link LocalDateTime}으로 변환한다.
 * 응답의 승인내역 순서는 변경하지 않는다.
 */
@Component
public class CardApprovalParser {

    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter
            .ofPattern("uuuuMMddHHmmss")
            .withResolverStyle(ResolverStyle.STRICT);

    /**
     * 한 카드의 검증된 국내 승인내역 한 페이지를 불변 내부 데이터 목록으로 변환한다.
     *
     * <p>호출자는 이 메서드보다 먼저 {@link CardApprovalValidator#validate(CardApprovalResponse)}를 호출해
     * 응답과 승인 목록의 유효성을 보장하고, 검증된 카드 목록에서 얻은 카드 ID를 전달해야 한다.
     * 승인내역이 없으면 예외 대신 빈 목록을 반환한다. {@link java.util.stream.Stream#toList()}
     * 결과를 사용하므로 반환 목록은 변경할 수 없다.</p>
     *
     * @param cardId 카드 목록 조회 응답에서 얻은 카드 고유 식별자
     * @param response {@link CardApprovalValidator} 검증을 통과한 국내 승인내역 응답
     * @return 원본 순서를 유지한 카드 승인내역 불변 목록
     */
    public List<CardApprovalData> parseApprovals(String cardId, CardApprovalResponse response) {
        // 승인내역의 원본 순서를 유지하면서 각 항목을 CardApprovalData로 변환한다.
        return response.approvals().stream()
                .map(approval -> toCardApprovalData(cardId, approval))
                .toList();
    }

    /**
     * 단일 Raw 승인내역에 카드 ID를 결합하고 Java 날짜 타입을 적용한다.
     *
     * @param cardId 카드 목록에서 전달받은 카드 고유 식별자
     * @param approval 검증을 통과한 단일 승인내역 항목
     * @return 내부 처리용 카드 승인 데이터 한 건
     */
    private CardApprovalData toCardApprovalData(
            String cardId,
            CardApprovalResponse.ApprovalItem approval
    ) {
        // 검증된 Raw 필드를 내부 모델의 필드 순서와 Java 타입에 맞춰 변환한다.
        return new CardApprovalData(
                cardId,
                approval.approvalNumber(),
                parseDateTime(approval.approvedDateTime()),
                approval.statusCode(),
                approval.payTypeCode(),
                parseOptionalDateTime(approval.transactionDateTime()),
                approval.merchantName(),
                approval.merchantRegistrationNumber(),
                approval.approvedAmount(),
                approval.modifiedAmount(),
                approval.totalInstallmentCount()
        );
    }

    /**
     * 검증된 필수 일시 문자열을 {@link LocalDateTime}으로 변환한다.
     *
     * @param value {@code yyyyMMddHHmmss} 형식의 필수 일시 문자열
     * @return 변환된 일시
     */
    private LocalDateTime parseDateTime(String value) {
        // Validator가 확인한 동일한 엄격 형식으로 필수 일시 문자열을 변환한다.
        return LocalDateTime.parse(value, DATE_TIME_FORMATTER);
    }

    /**
     * 미회신을 허용하는 선택 일시 문자열을 {@link LocalDateTime}으로 변환한다.
     *
     * @param value {@code null} 또는 {@code yyyyMMddHHmmss} 형식의 일시 문자열
     * @return 미회신이면 {@code null}, 회신됐으면 변환된 일시
     */
    private LocalDateTime parseOptionalDateTime(String value) {
        // 선택 일시가 미회신된 경우 내부 모델에도 null을 유지한다.
        if (value == null) {
            return null;
        }

        // 회신된 선택 일시를 Validator가 확인한 동일한 엄격 형식으로 변환한다.
        return parseDateTime(value);
    }
}
