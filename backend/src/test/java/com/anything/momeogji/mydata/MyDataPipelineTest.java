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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * user-01 Dummy JSON을 실제 수집·정제·집계·분류 구현으로 처리해 전체 파이프라인을 검증한다.
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
     * user-01의 동의 카드 한 장에서 현재 Dummy 승인내역 68건을 모두 수집하는지 확인한다.
     */
    @Test
    void user01의_동의_카드_승인내역_68건을_수집한다() {
        UserMyData result = myDataService.collect(1L);

        assertThat(result.userId()).isEqualTo(1L);
        assertThat(result.approvals()).hasSize(68);
        assertThat(result.approvals())
                .extracting(approval -> approval.cardId())
                .containsOnly("001");
    }

    /**
     * 점심 시간대 결제를 31개 가맹점으로 묶고 검색 실패 결과를 모두 미분류로 보존하는지 확인한다.
     */
    @Test
    void user01의_점심_결제를_31개_미분류_가맹점으로_가공한다() {
        TransformedUserMyData result = myDataService.process(
                1L,
                LocalTime.of(12, 0)
        );

        assertThat(result.userId()).isEqualTo(1L);
        assertThat(result.merchantUsages()).hasSize(31);
        assertThat(result.merchantUsages()).allSatisfy(merchantUsage -> {
            KakaoPlaceMatchData kakaoPlaceMatch = merchantUsage.kakaoPlaceMatch();
            assertThat(kakaoPlaceMatch.categoryCode())
                    .isEqualTo(KakaoPlaceMatchData.UNKNOWN_CATEGORY_CODE);
            assertThat(kakaoPlaceMatch.matchConfidence()).isZero();
            assertThat(kakaoPlaceMatch.placeId()).isNull();
            assertThat(kakaoPlaceMatch.placeName()).isNull();
            assertThat(kakaoPlaceMatch.longitude()).isNull();
            assertThat(kakaoPlaceMatch.latitude()).isNull();
        });

        assertThatThrownBy(() -> result.merchantUsages().add(null))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    /**
     * 현재 Dummy에 없는 아침과 저녁 시각을 선택하면 사용자 ID만 남긴 빈 결과가 반환되는지 확인한다.
     */
    @Test
    void 선택_시각의_시간대에_결제가_없으면_빈_최종_결과를_반환한다() {
        TransformedUserMyData morningResult = myDataService.process(
                1L,
                LocalTime.of(9, 0)
        );
        TransformedUserMyData dinnerResult = myDataService.process(
                1L,
                LocalTime.of(18, 0)
        );

        assertThat(morningResult.userId()).isEqualTo(1L);
        assertThat(morningResult.merchantUsages()).isEmpty();
        assertThat(dinnerResult.userId()).isEqualTo(1L);
        assertThat(dinnerResult.merchantUsages()).isEmpty();
    }
}
