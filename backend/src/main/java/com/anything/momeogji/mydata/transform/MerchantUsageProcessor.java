package com.anything.momeogji.mydata.transform;

import com.anything.momeogji.mydata.transform.model.CleanApprovalData;
import com.anything.momeogji.mydata.transform.model.MerchantUsageData;
import org.springframework.stereotype.Component;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 1차 정제된 승인내역에서 선택 시간대의 가맹점별 사용 이력을 만드는 컴포넌트
 *
 * 승인시각으로 시간대를 계산한 뒤 주최자가 선택한 시간대와 다른 내역을 먼저 제외
 * 남은 내역은 사업자등록번호와 비교용 상호명 조합으로 집계하고, 가맹점과 결제가 입력에서 처음 등장한 순서를 유지한 불변 목록으로 반환
 */
@Component
public class MerchantUsageProcessor {

    private final MerchantNameParser merchantNameParser;

    /**
     * 가맹점 집계 키를 만들 때 사용할 상호명 비교 컴포넌트를 주입받는다.
     *
     * @param merchantNameParser 원본 상호명을 변경하지 않고 비교 키를 만드는 컴포넌트
     */
    public MerchantUsageProcessor(MerchantNameParser merchantNameParser) {
        this.merchantNameParser = merchantNameParser;
    }

    /**
     * 1차 정제된 결제를 선택 시간대로 필터링하고 동일 가맹점별 사용 이력으로 집계
     * 입력 목록이 비어 있거나 선택 시간대에 해당하는 결제가 없으면 예외 없이 빈 불변 목록을 반환
     *
     * @param approvals 1차 정제와 정확한 결제 중복 제거가 끝난 승인내역 목록
     * @param meetingTime 옵션 계층에서 검증한 마이데이터 필터 기준 시각
     * @return 최초 가맹점 순서와 결제 순서를 유지한 가맹점별 사용 이력 불변 목록
     * @throws IllegalArgumentException 입력 목록 또는 시간대가 null인 경우
     */
    public List<MerchantUsageData> process(List<CleanApprovalData> approvals, LocalTime meetingTime) {
        // 집계할 1차 정제 목록과 선택 시각이 존재하는지 검증
        if (approvals == null) {
            throw new IllegalArgumentException("approvals는 null일 수 없습니다.");
        }
        if (meetingTime == null) {
            throw new IllegalArgumentException("meetingTime은 null일 수 없습니다.");
        }

        // 옵션 계층에서 받은 시각을 결제 필터링에만 사용할 내부 시간대로 변환한다.
        TimeBand meetingTimeBand = TimeBand.fromTime(meetingTime);
        Map<MerchantAggregationKey, List<CleanApprovalData>> groupedApprovals = new LinkedHashMap<>();

        for (CleanApprovalData approval : approvals) {
            // 승인시각을 세 개의 식사 시간대 중 하나로 분류한다.
            TimeBand approvalTimeBand = TimeBand.from(approval.approvedAt());

            // 주최자가 선택한 시간대와 다른 결제는 가맹점 집계 전에 제외한다.
            if (approvalTimeBand != meetingTimeBand) {
                continue;
            }

            // 사업자등록번호 기반 가맹점 코드와 비교용 상호명으로 집계 키를 생성
            MerchantAggregationKey aggregationKey = createAggregationKey(approval);

            // 같은 키의 결제를 최초 가맹점 등장 순서와 원본 결제 순서대로 누적
            groupedApprovals
                    .computeIfAbsent(aggregationKey, ignored -> new ArrayList<>())
                    .add(approval);
        }

        List<MerchantUsageData> merchantUsages = new ArrayList<>(groupedApprovals.size());

        // 각 가맹점 그룹의 승인시각·금액 쌍을 하나의 사용 이력 모델로 변환
        for (List<CleanApprovalData> merchantApprovals : groupedApprovals.values()) {
            merchantUsages.add(toMerchantUsageData(merchantApprovals));
        }

        // 호출자가 가맹점 순서나 집계 내용을 변경하지 못하도록 불변 목록 반환
        return List.copyOf(merchantUsages);
    }

    /**
     * 가맹점 정보 회신 형태에 따라 사업자등록번호와 비교용 상호명의 조합 키를 만든다.
     *
     * <p>번호와 이름이 모두 있으면 둘 다 사용하고, 하나만 있으면 회신된 값만 사용한다.
     * 따라서 같은 사업자등록번호라도 비교용 상호명이 다르면 서로 다른 가맹점 그룹이 된다.</p>
     *
     * @param approval 집계 키를 생성할 1차 정제 승인내역
     * @return 가맹점 그룹을 구분하는 전용 불변 키
     */
    private MerchantAggregationKey createAggregationKey(CleanApprovalData approval) {
        // 원본 상호명은 보존하고 집계 키에만 사용할 별도 비교 문자열을 만든다.
        String comparisonMerchantName = merchantNameParser.createComparisonKey(
                approval.merchantName()
        );

        // 번호·이름 회신 조합을 그대로 표현하여 서로 다른 키 유형의 충돌을 방지한다.
        return new MerchantAggregationKey(
                approval.merchantRegistrationNumber(),
                comparisonMerchantName
        );
    }

    /**
     * 같은 집계 키로 모인 결제를 승인시각·승인금액 쌍의 가맹점 사용 이력으로 변환
     *
     * @param approvals 같은 가맹점 키에 속하며 원본 순서를 유지한 결제 목록
     * @return 최초 결제의 원본 가맹점 정보를 유지한 가맹점별 사용 이력
     */
    private MerchantUsageData toMerchantUsageData(List<CleanApprovalData> approvals) {
        CleanApprovalData firstApproval = approvals.get(0);
        List<MerchantUsageData.PaymentLog> payments =
                new ArrayList<>(approvals.size());

        // 승인번호는 폐기하고 서로 짝을 이루는 승인시각과 승인금액만 결제 로그로 보존한다.
        for (CleanApprovalData approval : approvals) {
            payments.add(new MerchantUsageData.PaymentLog(
                    approval.approvedAt(),
                    approval.approvedAmount()
            ));
        }

        // 첫 결제의 원본 가맹점 정보와 미매칭 기본값을 사용해 분류 전 사용 이력을 만든다.
        return MerchantUsageData.unclassified(
                firstApproval.merchantName(),
                firstApproval.merchantRegistrationNumber(),
                payments
        );
    }

    /**
     * 사업자등록번호와 비교용 상호명의 조합으로 가맹점 그룹을 구분하는 전용 값이다.
     *
     * @param merchantCode 가맹점을 구분하는 사업자등록번호 기반 코드. 미회신이면 {@code null}
     * @param comparisonMerchantName NFKC·소문자·공백 제거가 적용된 비교용 상호명
     */
    private record MerchantAggregationKey(
            String merchantCode,
            String comparisonMerchantName
    ) {
    }
}
