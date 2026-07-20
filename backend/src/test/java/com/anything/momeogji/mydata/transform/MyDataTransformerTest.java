package com.anything.momeogji.mydata.transform;

import com.anything.momeogji.mydata.model.UserMyData;
import com.anything.momeogji.mydata.transform.model.CleanApprovalData;
import com.anything.momeogji.mydata.transform.model.KakaoPlaceMatchData;
import com.anything.momeogji.mydata.transform.model.MerchantUsageData;
import com.anything.momeogji.mydata.transform.model.TimeBand;
import com.anything.momeogji.mydata.transform.model.TransformedUserMyData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * MyDataTransformer가 기존 가공 컴포넌트를 정해진 순서와 계약으로 조립하는지 검증한다.
 */
@ExtendWith(MockitoExtension.class)
class MyDataTransformerTest {

    @Mock
    private UserMyDataCleaner userMyDataCleaner;

    @Mock
    private MerchantUsageProcessor merchantUsageProcessor;

    @Mock
    private MerchantClassificationProcessor merchantClassificationProcessor;

    private MyDataTransformer myDataTransformer;

    /**
     * 테스트마다 동일한 세 단계 가공 컴포넌트를 주입한 Transformer를 생성한다.
     */
    @BeforeEach
    void setUp() {
        myDataTransformer = new MyDataTransformer(
                userMyDataCleaner,
                merchantUsageProcessor,
                merchantClassificationProcessor
        );
    }

    /**
     * 정제 결과가 집계로, 집계 결과가 분류로 전달되고 최종 메타데이터가 보존되는지 확인한다.
     */
    @Test
    void 정제_집계_분류_순서로_가공하고_최종_결과를_조립한다() {
        UserMyData userMyData = new UserMyData(1L, List.of());
        LocalDateTime approvedAt = LocalDateTime.of(2026, 7, 18, 13, 20);
        CleanApprovalData cleanApproval = new CleanApprovalData(
                "14263337",
                approvedAt,
                "영인성",
                "211-75-37672",
                BigDecimal.valueOf(11_000)
        );
        MerchantUsageData merchantUsage = MerchantUsageData.unclassified(
                "영인성",
                "211-75-37672",
                TimeBand.LUNCH,
                List.of(new MerchantUsageData.PaymentLog(
                        approvedAt,
                        BigDecimal.valueOf(11_000)
                ))
        );
        MerchantUsageData classifiedUsage = merchantUsage.withKakaoPlaceMatch(
                new KakaoPlaceMatchData(
                        KakaoPlaceMatchData.RESTAURANT_CATEGORY_CODE,
                        "123456",
                        "영인성",
                        100,
                        new BigDecimal("127.12345"),
                        new BigDecimal("37.12345")
                )
        );
        List<CleanApprovalData> cleanedApprovals = List.of(cleanApproval);
        List<MerchantUsageData> merchantUsages = List.of(merchantUsage);
        List<MerchantUsageData> classifiedUsages = List.of(classifiedUsage);

        given(userMyDataCleaner.clean(userMyData)).willReturn(cleanedApprovals);
        given(merchantUsageProcessor.process(cleanedApprovals, TimeBand.LUNCH))
                .willReturn(merchantUsages);
        given(merchantClassificationProcessor.classify(merchantUsages))
                .willReturn(classifiedUsages);

        TransformedUserMyData result = myDataTransformer.transform(
                userMyData,
                TimeBand.LUNCH
        );

        assertThat(result.participantId()).isEqualTo(1L);
        assertThat(result.selectedTimeBand()).isEqualTo(TimeBand.LUNCH);
        assertThat(result.merchantUsages()).containsExactly(classifiedUsage);

        InOrder processingOrder = inOrder(
                userMyDataCleaner,
                merchantUsageProcessor,
                merchantClassificationProcessor
        );
        processingOrder.verify(userMyDataCleaner).clean(userMyData);
        processingOrder.verify(merchantUsageProcessor).process(
                cleanedApprovals,
                TimeBand.LUNCH
        );
        processingOrder.verify(merchantClassificationProcessor).classify(merchantUsages);
    }

    /**
     * 각 단계에 처리할 데이터가 없어도 참가자와 시간대를 포함한 정상 빈 결과를 만드는지 확인한다.
     */
    @Test
    void 처리할_승인내역이_없으면_빈_최종_결과를_반환한다() {
        UserMyData userMyData = new UserMyData(1L, List.of());

        given(userMyDataCleaner.clean(userMyData)).willReturn(List.of());
        given(merchantUsageProcessor.process(List.of(), TimeBand.MORNING))
                .willReturn(List.of());
        given(merchantClassificationProcessor.classify(List.of()))
                .willReturn(List.of());

        TransformedUserMyData result = myDataTransformer.transform(
                userMyData,
                TimeBand.MORNING
        );

        assertThat(result.participantId()).isEqualTo(1L);
        assertThat(result.selectedTimeBand()).isEqualTo(TimeBand.MORNING);
        assertThat(result.merchantUsages()).isEmpty();
    }

    /**
     * 중간 집계 단계의 오류가 빈 결과로 바뀌지 않고 그대로 호출자에게 전달되는지 확인한다.
     */
    @Test
    void 중간_단계_예외를_감추지_않고_전달한다() {
        UserMyData userMyData = new UserMyData(1L, List.of());
        IllegalStateException failure = new IllegalStateException("집계 실패");

        given(userMyDataCleaner.clean(userMyData)).willReturn(List.of());
        given(merchantUsageProcessor.process(List.of(), TimeBand.LUNCH))
                .willThrow(failure);

        assertThatThrownBy(() -> myDataTransformer.transform(
                userMyData,
                TimeBand.LUNCH
        )).isSameAs(failure);

        verifyNoInteractions(merchantClassificationProcessor);
    }

    /**
     * 선택 시간대가 없으면 불필요한 정제 작업을 시작하기 전에 요청을 거부하는지 확인한다.
     */
    @Test
    void 선택_시간대가_없으면_가공_컴포넌트를_호출하지_않는다() {
        UserMyData userMyData = new UserMyData(1L, List.of());

        assertThatThrownBy(() -> myDataTransformer.transform(userMyData, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("selectedTimeBand는 필수입니다.");

        verifyNoInteractions(
                userMyDataCleaner,
                merchantUsageProcessor,
                merchantClassificationProcessor
        );
    }

    /**
     * 최종 결과 모델이 필수 메타데이터를 검증하고 전달받은 목록을 방어적으로 복사하는지 확인한다.
     */
    @Test
    void 최종_결과는_필수값을_검증하고_분류_목록을_불변으로_보존한다() {
        List<MerchantUsageData> mutableMerchantUsages = new ArrayList<>();
        TransformedUserMyData result = new TransformedUserMyData(
                1L,
                TimeBand.LUNCH,
                mutableMerchantUsages
        );

        mutableMerchantUsages.add(null);

        assertThat(result.merchantUsages()).isEmpty();
        assertThatThrownBy(() -> result.merchantUsages().add(null))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> new TransformedUserMyData(
                0L,
                TimeBand.LUNCH,
                List.of()
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("participantId는 1 이상이어야 합니다.");
        assertThatThrownBy(() -> new TransformedUserMyData(
                1L,
                null,
                List.of()
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("selectedTimeBand는 필수입니다.");
        assertThatThrownBy(() -> new TransformedUserMyData(
                1L,
                TimeBand.LUNCH,
                null
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("merchantUsages는 null일 수 없습니다.");
    }
}
