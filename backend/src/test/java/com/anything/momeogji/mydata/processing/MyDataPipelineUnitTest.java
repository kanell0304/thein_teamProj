package com.anything.momeogji.mydata.processing;

import com.anything.momeogji.mydata.collection.model.CollectedUserMyData;
import com.anything.momeogji.mydata.processing.model.CleanedApprovalData;
import com.anything.momeogji.mydata.processing.model.KakaoPlaceMatchData;
import com.anything.momeogji.mydata.processing.model.MerchantUsageData;
import com.anything.momeogji.mydata.processing.model.MyDataRestaurantData;
import com.anything.momeogji.mydata.processing.place.MerchantPlaceClassifier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * MyDataPipeline이 기존 가공 컴포넌트를 정해진 순서와 계약으로 조립하는지 검증한다.
 */
@ExtendWith(MockitoExtension.class)
class MyDataPipelineUnitTest {

    @Mock
    private UserMyDataCleaner userMyDataCleaner;

    @Mock
    private MerchantUsageProcessor merchantUsageProcessor;

    @Mock
    private MerchantPlaceClassifier merchantPlaceClassifier;

    private MyDataPipeline myDataPipeline;

    /**
     * 테스트마다 동일한 세 단계 가공 컴포넌트를 주입한 파이프라인을 생성한다.
     */
    @BeforeEach
    void setUp() {
        myDataPipeline = new MyDataPipeline(
                userMyDataCleaner,
                merchantUsageProcessor,
                merchantPlaceClassifier
        );
    }

    /**
     * 정제 결과가 집계와 분류를 거쳐 음식점명·음식 카테고리 최종 목록으로 변환되는지 확인한다.
     */
    @Test
    void 정제_집계_분류_순서로_가공하고_최종_결과를_조립한다() {
        CollectedUserMyData collectedMyData = new CollectedUserMyData(1L, List.of());
        LocalDateTime approvedAt = LocalDateTime.of(2026, 7, 18, 13, 20);
        CleanedApprovalData cleanedApproval = new CleanedApprovalData(
                "14263337",
                approvedAt,
                "영인성",
                "211-75-37672",
                BigDecimal.valueOf(11_000)
        );
        MerchantUsageData merchantUsage = MerchantUsageData.withoutPlaceMatch(
                "영인성",
                "211-75-37672",
                List.of(new MerchantUsageData.PaymentLog(
                        approvedAt,
                        BigDecimal.valueOf(11_000)
                ))
        );
        MerchantUsageData classifiedUsage = merchantUsage.withKakaoPlaceMatch(
                new KakaoPlaceMatchData(
                        "영인성",
                        "음식점 > 중식 > 중화요리"
                )
        );
        List<CleanedApprovalData> cleanedApprovals = List.of(cleanedApproval);
        List<MerchantUsageData> merchantUsages = List.of(merchantUsage);
        List<MerchantUsageData> classifiedUsages = List.of(classifiedUsage);
        LocalTime meetingTime = LocalTime.of(12, 0);
        String categoryGroupCode = "FD6";

        given(userMyDataCleaner.clean(collectedMyData)).willReturn(cleanedApprovals);
        given(merchantUsageProcessor.process(cleanedApprovals, meetingTime))
                .willReturn(merchantUsages);
        given(merchantPlaceClassifier.classify(merchantUsages, categoryGroupCode))
                .willReturn(classifiedUsages);

        List<MyDataRestaurantData> result = myDataPipeline.execute(
                collectedMyData,
                meetingTime,
                categoryGroupCode
        );

        assertThat(result).containsExactly(
                new MyDataRestaurantData(
                        "영인성",
                        "중식 > 중화요리"
                )
        );
        assertThatThrownBy(() -> result.add(
                new MyDataRestaurantData("상무초밥", "일식 > 초밥")
        )).isInstanceOf(UnsupportedOperationException.class);

        InOrder processingOrder = inOrder(
                userMyDataCleaner,
                merchantUsageProcessor,
                merchantPlaceClassifier
        );
        processingOrder.verify(userMyDataCleaner).clean(collectedMyData);
        processingOrder.verify(merchantUsageProcessor).process(
                cleanedApprovals,
                meetingTime
        );
        processingOrder.verify(merchantPlaceClassifier).classify(
                merchantUsages,
                categoryGroupCode
        );
    }

    /**
     * 각 단계에 처리할 데이터가 없어도 정상 빈 음식점 목록을 만드는지 확인한다.
     */
    @Test
    void 처리할_승인내역이_없으면_빈_최종_결과를_반환한다() {
        CollectedUserMyData collectedMyData = new CollectedUserMyData(1L, List.of());
        LocalTime meetingTime = LocalTime.of(9, 0);
        String categoryGroupCode = "FD6";

        given(userMyDataCleaner.clean(collectedMyData)).willReturn(List.of());
        given(merchantUsageProcessor.process(List.of(), meetingTime))
                .willReturn(List.of());
        given(merchantPlaceClassifier.classify(List.of(), categoryGroupCode))
                .willReturn(List.of());

        List<MyDataRestaurantData> result = myDataPipeline.execute(
                collectedMyData,
                meetingTime,
                categoryGroupCode
        );

        assertThat(result).isEmpty();
    }

    /**
     * 중간 집계 단계의 오류가 빈 결과로 바뀌지 않고 그대로 호출자에게 전달되는지 확인한다.
     */
    @Test
    void 중간_단계_예외를_감추지_않고_전달한다() {
        CollectedUserMyData collectedMyData = new CollectedUserMyData(1L, List.of());
        IllegalStateException failure = new IllegalStateException("집계 실패");
        LocalTime meetingTime = LocalTime.of(12, 0);

        given(userMyDataCleaner.clean(collectedMyData)).willReturn(List.of());
        given(merchantUsageProcessor.process(List.of(), meetingTime))
                .willThrow(failure);

        assertThatThrownBy(() -> myDataPipeline.execute(
                collectedMyData,
                meetingTime,
                "FD6"
        )).isSameAs(failure);

        verifyNoInteractions(merchantPlaceClassifier);
    }

    /**
     * 선택 시각이 없으면 불필요한 정제 작업을 시작하기 전에 요청을 거부하는지 확인한다.
     */
    @Test
    void 선택_시각이_없으면_가공_컴포넌트를_호출하지_않는다() {
        CollectedUserMyData collectedMyData = new CollectedUserMyData(1L, List.of());

        assertThatThrownBy(() -> myDataPipeline.execute(collectedMyData, null, "FD6"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("meetingTime은 필수입니다.");

        verifyNoInteractions(
                userMyDataCleaner,
                merchantUsageProcessor,
                merchantPlaceClassifier
        );
    }

    /**
     * 최종 음식점 항목이 음식점명과 음식 카테고리의 필수값을 검증하는지 확인한다.
     */
    @Test
    void 최종_음식점_항목은_필수값을_검증한다() {
        assertThatThrownBy(() -> new MyDataRestaurantData(
                " ",
                "중식 > 중화요리"
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("restaurantName은 필수입니다.");
        assertThatThrownBy(() -> new MyDataRestaurantData(
                "영인성",
                null
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("foodCategory는 필수입니다.");
    }

    /**
     * 음식점·카페 대분류만 제거하고 인식할 수 없는 첫 카테고리는 전체 경로를 유지하는지 확인한다.
     */
    @Test
    void 카테고리_대분류만_제거한다() {
        CollectedUserMyData collectedMyData = new CollectedUserMyData(1L, List.of());
        LocalTime meetingTime = LocalTime.NOON;
        List<MerchantUsageData> classifiedUsages = List.of(
                MerchantUsageData.withoutPlaceMatch(
                        "카페원본",
                        "100-00-00001",
                        List.of(new MerchantUsageData.PaymentLog(
                                LocalDateTime.of(2026, 7, 18, 13, 0),
                                BigDecimal.valueOf(5_000)
                        ))
                ).withKakaoPlaceMatch(new KakaoPlaceMatchData(
                        "카페장소",
                        "카페 > 커피전문점"
                )),
                MerchantUsageData.withoutPlaceMatch(
                        "한식원본",
                        "100-00-00002",
                        List.of(new MerchantUsageData.PaymentLog(
                                LocalDateTime.of(2026, 7, 18, 13, 10),
                                BigDecimal.valueOf(10_000)
                        ))
                ).withKakaoPlaceMatch(new KakaoPlaceMatchData(
                        "한식장소",
                        "한식 > 백반"
                ))
        );

        given(userMyDataCleaner.clean(collectedMyData)).willReturn(List.of());
        given(merchantUsageProcessor.process(List.of(), meetingTime))
                .willReturn(List.of());
        given(merchantPlaceClassifier.classify(List.of(), "FD6"))
                .willReturn(classifiedUsages);

        List<MyDataRestaurantData> result = myDataPipeline.execute(
                collectedMyData,
                meetingTime,
                "FD6"
        );

        assertThat(result).containsExactly(
                new MyDataRestaurantData("카페장소", "커피전문점"),
                new MyDataRestaurantData("한식장소", "한식 > 백반")
        );
    }
}
