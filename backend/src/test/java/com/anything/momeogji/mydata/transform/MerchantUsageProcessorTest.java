package com.anything.momeogji.mydata.transform;

import com.anything.momeogji.mydata.transform.model.CleanApprovalData;
import com.anything.momeogji.mydata.transform.model.MerchantUsageData;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link MerchantUsageProcessor}가 옵션 시각과 승인 시각을 내부 시간대로 분류해 필요한 결제만 남기는지 검증한다.
 */
class MerchantUsageProcessorTest {

    private final MerchantUsageProcessor merchantUsageProcessor =
            new MerchantUsageProcessor(new MerchantNameParser());

    /**
     * 아침·점심·저녁 경계 직전과 경계 시각이 정의된 구간에 정확히 포함되는지 확인한다.
     */
    @Test
    void 선택_시각과_승인_시각을_내부_시간대_경계에_맞춰_필터링한다() {
        List<CleanApprovalData> approvals = List.of(
                approval("01:59", 1, 59),
                approval("02:00", 2, 0),
                approval("09:59", 9, 59),
                approval("10:00", 10, 0),
                approval("15:59", 15, 59),
                approval("16:00", 16, 0)
        );

        List<MerchantUsageData> morning = merchantUsageProcessor.process(
                approvals,
                LocalTime.of(9, 59)
        );
        List<MerchantUsageData> lunch = merchantUsageProcessor.process(
                approvals,
                LocalTime.of(10, 0)
        );
        List<MerchantUsageData> dinner = merchantUsageProcessor.process(
                approvals,
                LocalTime.of(16, 0)
        );

        assertThat(morning)
                .extracting(MerchantUsageData::merchantName)
                .containsExactly("가맹점-02:00", "가맹점-09:59");
        assertThat(lunch)
                .extracting(MerchantUsageData::merchantName)
                .containsExactly("가맹점-10:00", "가맹점-15:59");
        assertThat(dinner)
                .extracting(MerchantUsageData::merchantName)
                .containsExactly("가맹점-01:59", "가맹점-16:00");
    }

    /**
     * 경계 검증에 사용할 승인내역 한 건을 생성한다.
     *
     * @param label 가맹점과 승인번호를 구분할 시각 라벨
     * @param hour 승인 시각의 시
     * @param minute 승인 시각의 분
     * @return 서로 다른 가맹점으로 집계되는 정상 승인내역
     */
    private CleanApprovalData approval(String label, int hour, int minute) {
        return new CleanApprovalData(
                "approval-" + label,
                LocalDateTime.of(2026, 7, 18, hour, minute),
                "가맹점-" + label,
                "merchant-" + label,
                BigDecimal.valueOf(10_000)
        );
    }
}
