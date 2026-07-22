package com.anything.momeogji.mydata;

import com.anything.momeogji.mydata.cardapproval.CardApprovalParser;
import com.anything.momeogji.mydata.cardapproval.CardApprovalValidator;
import com.anything.momeogji.mydata.cardlist.CardListParser;
import com.anything.momeogji.mydata.cardlist.CardListValidator;
import com.anything.momeogji.mydata.model.UserMyData;
import com.anything.momeogji.mydata.transform.MerchantClassificationProcessor;
import com.anything.momeogji.mydata.transform.MerchantMatchScoreCalculator;
import com.anything.momeogji.mydata.transform.MerchantNameParser;
import com.anything.momeogji.mydata.transform.MerchantPlaceMatcher;
import com.anything.momeogji.mydata.transform.MerchantUsageProcessor;
import com.anything.momeogji.mydata.transform.MyDataTransformer;
import com.anything.momeogji.mydata.transform.UserMyDataCleaner;
import com.anything.momeogji.mydata.transform.local.MerchantPlaceSearchClient;
import com.anything.momeogji.mydata.transform.model.KakaoPlaceMatchData;
import com.anything.momeogji.mydata.transform.model.TransformedUserMyData;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * user-01~03 실행용 Dummy JSON을 실제 수집·정제·집계·분류 구현으로 처리한다.
 */
class MyDataPipelineTest {

    private MyDataService myDataService;

    /**
     * 외부 카카오 호출만 빈 후보 Client로 대체하고 나머지는 실제 구현 객체로 파이프라인을 구성한다.
     */
    @BeforeEach
    void setUp() {
        MerchantNameParser merchantNameParser = new MerchantNameParser();
        MerchantPlaceSearchClient noCandidateSearchClient = query -> List.of();
        MerchantPlaceMatcher merchantPlaceMatcher = new MerchantPlaceMatcher(
                noCandidateSearchClient,
                merchantNameParser,
                new MerchantMatchScoreCalculator(merchantNameParser)
        );
        MyDataTransformer myDataTransformer = new MyDataTransformer(
                new UserMyDataCleaner(),
                new MerchantUsageProcessor(merchantNameParser),
                new MerchantClassificationProcessor(merchantPlaceMatcher)
        );

        myDataService = new MyDataService(
                new ObjectMapper(),
                new DummyMyDataProvider(),
                new CardListValidator(),
                new CardListParser(),
                new CardApprovalValidator(),
                new CardApprovalParser(),
                myDataTransformer
        );
    }

    /**
     * user-01은 미동의 card-003을 호출하지 않고 동의 카드 두 장만 수집한다.
     */
    @Test
    void user01은_동의_카드_두_장의_승인내역_92건을_수집한다() {
        UserMyData result = myDataService.collect(1L);

        assertThat(result.userId()).isEqualTo(1L);
        assertThat(result.approvals()).hasSize(92);
        assertThat(result.approvals().stream()
                .collect(java.util.stream.Collectors.groupingBy(
                        approval -> approval.cardId(),
                        java.util.stream.Collectors.counting()
                )))
                .containsExactlyInAnyOrderEntriesOf(Map.of("001", 68L, "002", 24L));
    }

    /**
     * user-02의 세 승인 페이지를 연결해 계획한 상태별 68건을 모두 수집한다.
     */
    @Test
    void user02는_세_페이지의_승인내역_68건을_수집한다() {
        UserMyData result = myDataService.collect(2L);

        assertThat(result.approvals()).hasSize(68);
        assertThat(result.approvals().stream()
                .collect(java.util.stream.Collectors.groupingBy(
                        approval -> approval.statusCode(),
                        java.util.stream.Collectors.counting()
                )))
                .containsExactlyInAnyOrderEntriesOf(
                        Map.of("01", 60L, "02", 3L, "03", 2L, "04", 3L)
                );
    }

    /**
     * 취소·정정을 제외하고 승인·무승인매입을 추천 가공 대상으로 유지한다.
     */
    @Test
    void user02의_취소와_정정은_정제에서_제외된다() {
        UserMyData collected = myDataService.collect(2L);
        var cleaned = new UserMyDataCleaner().clean(collected);

        assertThat(cleaned).hasSize(63);
        assertThat(collected.approvals())
                .filteredOn(approval -> "04".equals(approval.statusCode()))
                .hasSize(3);
        assertThat(cleaned)
                .extracting(approval -> approval.approvalNumber())
                .containsAll(collected.approvals().stream()
                        .filter(approval -> "04".equals(approval.statusCode()))
                        .map(approval -> approval.approvalNumber())
                        .toList());
    }

    /**
     * user-03의 빈 card-003을 포함한 네 카드 흐름과 세 시간대 경계 fixture를 처리한다.
     */
    @Test
    void user03은_빈_카드를_포함해_36건을_수집하고_시간대별로_가공한다() {
        UserMyData collected = myDataService.collect(3L);
        TransformedUserMyData morningResult = myDataService.process(
                3L,
                LocalTime.of(9, 0)
        );
        TransformedUserMyData lunchResult = myDataService.process(
                3L,
                LocalTime.of(12, 0)
        );
        TransformedUserMyData dinnerResult = myDataService.process(
                3L,
                LocalTime.of(18, 0)
        );

        assertThat(collected.approvals()).hasSize(36);
        assertThat(collected.approvals().stream()
                .collect(java.util.stream.Collectors.groupingBy(
                        approval -> approval.cardId(),
                        java.util.stream.Collectors.counting()
                )))
                .containsExactlyInAnyOrderEntriesOf(
                        Map.of("001", 12L, "002", 12L, "004", 12L)
                );
        assertThat(morningResult.merchantUsages()).hasSize(12);
        assertThat(lunchResult.merchantUsages()).hasSize(11);
        assertThat(dinnerResult.merchantUsages()).hasSize(12);
        assertThat(morningResult.merchantUsages())
                .allSatisfy(merchantUsage -> assertUnknownPlace(merchantUsage.kakaoPlaceMatch()));
    }

    private void assertUnknownPlace(KakaoPlaceMatchData kakaoPlaceMatch) {
        assertThat(kakaoPlaceMatch.categoryCode())
                .isEqualTo(KakaoPlaceMatchData.UNKNOWN_CATEGORY_CODE);
        assertThat(kakaoPlaceMatch.matchConfidence()).isZero();
        assertThat(kakaoPlaceMatch.placeId()).isNull();
        assertThat(kakaoPlaceMatch.placeName()).isNull();
        assertThat(kakaoPlaceMatch.longitude()).isNull();
        assertThat(kakaoPlaceMatch.latitude()).isNull();
    }
}
