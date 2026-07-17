package com.anything.momeogji.mydata.transform;

import com.anything.momeogji.mydata.model.CardApprovalData;
import com.anything.momeogji.mydata.model.UserMyData;
import com.anything.momeogji.mydata.transform.model.CleanApprovalData;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * UserMyData에서 추천 가공에 사용할 수 있는 결제만 1차 정제한다.
 *
 * <p>{@link UserMyData}를 입력받아 승인 또는 무승인 매입 상태이면서 가맹점명과
 * 사업자등록번호 중 하나 이상이 있는 결제만 {@link CleanApprovalData}로 변환한다.
 * 변환 결과에서 정확히 같은 결제가 반복되면 처음 등장한 항목만 유지하며,
 * 입력 승인내역의 원래 순서를 보존한 불변 목록을 반환한다.</p>
 */
@Component
public class UserMyDataCleaner {

    private static final Set<String> RETAINED_STATUS_CODES = Set.of("01", "04");

    /**
     * 참가자의 승인내역에서 제외 상태와 가맹점 정보 누락 항목을 제거하고 정확한 중복을 합친다.
     *
     * <p>승인내역이 비어 있거나 모든 항목이 제외 대상이면 예외 없이 빈 불변 목록을 반환한다.
     * 입력 객체가 {@code null}이면 정제 대상을 식별할 수 없으므로 예외를 발생시킨다.</p>
     *
     * @param userMyData 수집·검증·파싱이 완료된 참가자 마이데이터
     * @return 최초 등장 순서를 유지하고 정확한 중복을 제거한 1차 정제 결과
     * @throws IllegalArgumentException 입력 객체가 {@code null}인 경우
     */
    public List<CleanApprovalData> clean(UserMyData userMyData) {
        // 정제할 참가자 마이데이터 객체가 존재하는지 검증한다.
        if (userMyData == null) {
            throw new IllegalArgumentException("userMyData는 null일 수 없습니다.");
        }

        Map<ApprovalDuplicateKey, CleanApprovalData> uniqueApprovals = new LinkedHashMap<>();

        for (CardApprovalData approval : userMyData.approvals()) {
            // 승인 또는 무승인 매입 상태만 유지하고 취소·정정 상태는 제외한다.
            if (!isRetainedStatus(approval)) {
                continue;
            }

            // 가맹점명과 사업자등록번호가 모두 누락된 결제는 후속 분류 대상에서 제외한다.
            if (!hasMerchantInformation(approval)) {
                continue;
            }

            // 후속 가공에 필요한 필드만 1차 정제 내부 모델로 변환한다.
            CleanApprovalData cleanApproval = toCleanApprovalData(approval);

            // 카드와 무관하게 정확히 같은 결제를 판별할 전용 중복 키를 생성한다.
            ApprovalDuplicateKey duplicateKey = createDuplicateKey(cleanApproval);

            // 같은 중복 키가 처음 나타난 결제만 저장하여 입력 순서를 유지한다.
            uniqueApprovals.putIfAbsent(duplicateKey, cleanApproval);
        }

        // 호출자가 정제 결과의 순서나 내용을 변경하지 못하도록 불변 목록으로 반환한다.
        return List.copyOf(uniqueApprovals.values());
    }

    /**
     * 승인내역의 상태가 1차 정제에서 유지할 승인 또는 무승인 매입 상태인지 확인한다.
     *
     * @param approval 상태를 확인할 카드 승인내역
     * @return 상태 코드가 {@code 01} 또는 {@code 04}이면 {@code true}
     */
    private boolean isRetainedStatus(CardApprovalData approval) {
        return RETAINED_STATUS_CODES.contains(approval.statusCode());
    }

    /**
     * 후속 가맹점 검색에 사용할 가맹점명 또는 사업자등록번호가 하나 이상 있는지 확인한다.
     *
     * @param approval 가맹점 정보를 확인할 카드 승인내역
     * @return 두 가맹점 필드 중 하나 이상이 공백이 아닌 값이면 {@code true}
     */
    private boolean hasMerchantInformation(CardApprovalData approval) {
        String merchantName = approval.merchantName();
        String merchantRegistrationNumber = approval.merchantRegistrationNumber();

        return (merchantName != null && !merchantName.isBlank())
                || (merchantRegistrationNumber != null && !merchantRegistrationNumber.isBlank());
    }

    /**
     * 원본 승인내역에서 1차 정제 이후에도 필요한 필드만 복사한다.
     *
     * @param approval 변환할 카드 승인내역
     * @return 원본 값을 정규화하지 않고 보존한 1차 정제 결과
     */
    private CleanApprovalData toCleanApprovalData(CardApprovalData approval) {
        return new CleanApprovalData(
                approval.approvalNumber(),
                approval.approvedAt(),
                approval.merchantName(),
                approval.merchantRegistrationNumber(),
                approval.approvedAmount()
        );
    }

    /**
     * 카드 식별자를 제외한 결제 속성으로 정확한 중복 판별 키를 만든다.
     *
     * @param approval 중복 키를 생성할 1차 정제 결과
     * @return 동일 결제를 판별하는 데만 사용하는 값 객체
     */
    private ApprovalDuplicateKey createDuplicateKey(CleanApprovalData approval) {
        return new ApprovalDuplicateKey(
                approval.approvalNumber(),
                approval.approvedAt(),
                approval.merchantRegistrationNumber(),
                approval.merchantName(),
                approval.approvedAmount()
        );
    }

    /**
     * 카드 구분 없이 정확히 같은 결제가 반복됐는지 판별하기 위한 전용 불변 값이다.
     *
     * @param approvalNumber 카드사가 발행한 승인번호
     * @param approvedAt 최초 승인일시
     * @param merchantRegistrationNumber 가맹점 사업자등록번호
     * @param merchantName 가맹점명
     * @param approvedAmount 최초 승인금액
     */
    private record ApprovalDuplicateKey(
            String approvalNumber,
            LocalDateTime approvedAt,
            String merchantRegistrationNumber,
            String merchantName,
            BigDecimal approvedAmount
    ) {
    }
}
