package com.anything.momeogji.mydata.transform;

import com.anything.momeogji.mydata.transform.local.MerchantPlaceSearchClient;
import com.anything.momeogji.mydata.transform.local.MerchantPlaceSearchClient.SearchCandidate;
import com.anything.momeogji.mydata.transform.model.KakaoPlaceMatchData;
import com.anything.momeogji.mydata.transform.model.MerchantUsageData;
import com.anything.momeogji.mydata.transform.model.TimeBand;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link MerchantClassificationProcessor}가 카카오 후보를 최종 {@link MerchantUsageData}에 올바르게 반영하는지 검증한다.
 */
class MerchantClassificationProcessorTest {

    /**
     * 완전히 일치하는 음식점 후보를 선택하고 원본 사용 이력과 절삭된 좌표를 보존하는지 확인한다.
     */
    @Test
    void 완전일치_음식점_후보를_사용_이력에_결합한다() {
        MerchantUsageData input = createUnclassifiedUsage("영인성");
        MerchantClassificationProcessor processor = createProcessor(query -> List.of(
                new SearchCandidate(
                        "1001",
                        "영인성",
                        KakaoPlaceMatchData.RESTAURANT_CATEGORY_CODE,
                        "127.123456",
                        "37.987654"
                )
        ));

        List<MerchantUsageData> result = processor.classify(List.of(input));

        assertThat(result).hasSize(1);
        MerchantUsageData classified = result.get(0);
        assertThat(classified).isNotSameAs(input);
        assertThat(classified.merchantName()).isEqualTo(input.merchantName());
        assertThat(classified.merchantCode()).isEqualTo(input.merchantCode());
        assertThat(classified.payments()).isEqualTo(input.payments());
        assertThat(classified.kakaoPlaceMatch().categoryCode())
                .isEqualTo(KakaoPlaceMatchData.RESTAURANT_CATEGORY_CODE);
        assertThat(classified.kakaoPlaceMatch().placeId()).isEqualTo("1001");
        assertThat(classified.kakaoPlaceMatch().matchConfidence()).isEqualTo(100);
        assertThat(classified.kakaoPlaceMatch().longitude())
                .isEqualByComparingTo("127.12345");
        assertThat(classified.kakaoPlaceMatch().latitude())
                .isEqualByComparingTo("37.98765");
    }

    /**
     * 검색 결과가 없으면 집계 단계에서 생성한 미매칭 사용 이력을 그대로 유지하는지 확인한다.
     */
    @Test
    void 검색_결과가_없으면_미매칭_사용_이력을_유지한다() {
        MerchantUsageData input = createUnclassifiedUsage("영인성");
        MerchantClassificationProcessor processor = createProcessor(query -> List.of());

        List<MerchantUsageData> result = processor.classify(List.of(input));

        assertThat(result).hasSize(1);
        assertThat(result.get(0)).isSameAs(input);
        assertThat(result.get(0).kakaoPlaceMatch())
                .isEqualTo(KakaoPlaceMatchData.unmatched());
        assertThatThrownBy(() -> result.add(input))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    /**
     * 같은 점수의 음식점·카페 후보가 충돌하면 첫 장소 추적값과 UNKNOWN 카테고리를 보존하는지 확인한다.
     */
    @Test
    void 최고점_후보의_카테고리가_충돌하면_UNKNOWN으로_보존한다() {
        MerchantUsageData input = createUnclassifiedUsage("영인성");
        MerchantClassificationProcessor processor = createProcessor(query -> List.of(
                new SearchCandidate(
                        "1001",
                        "영인성",
                        KakaoPlaceMatchData.RESTAURANT_CATEGORY_CODE,
                        "127.1",
                        "37.1"
                ),
                new SearchCandidate(
                        "1002",
                        "영인성",
                        KakaoPlaceMatchData.CAFE_CATEGORY_CODE,
                        "127.2",
                        "37.2"
                )
        ));

        MerchantUsageData result = processor.classify(List.of(input)).get(0);
        KakaoPlaceMatchData kakaoPlaceMatch = result.kakaoPlaceMatch();

        assertThat(kakaoPlaceMatch.categoryCode())
                .isEqualTo(KakaoPlaceMatchData.UNKNOWN_CATEGORY_CODE);
        assertThat(kakaoPlaceMatch.placeId()).isEqualTo("1001");
        assertThat(kakaoPlaceMatch.placeName()).isEqualTo("영인성");
        assertThat(kakaoPlaceMatch.matchConfidence()).isEqualTo(100);
        assertThat(kakaoPlaceMatch.longitude()).isNull();
        assertThat(kakaoPlaceMatch.latitude()).isNull();
    }

    /**
     * 이름은 일치하지만 음식점·카페가 아닌 명시적 카카오 카테고리를 최종 목록에서 제외하는지 확인한다.
     */
    @Test
    void 음식점과_카페가_아닌_카테고리는_제외한다() {
        MerchantUsageData input = createUnclassifiedUsage("테스트 편의점");
        MerchantClassificationProcessor processor = createProcessor(query -> List.of(
                new SearchCandidate(
                        "2001",
                        "테스트 편의점",
                        "CS2",
                        "127.1",
                        "37.1"
                )
        ));

        assertThat(processor.classify(List.of(input))).isEmpty();
    }

    /**
     * 누락·숫자 형식 오류·범위 오류 좌표를 장소 분류 실패가 아닌 좌표 미보강으로 처리하는지 확인한다.
     */
    @Test
    void 잘못된_외부_좌표는_둘_다_null로_처리한다() {
        List<InvalidCoordinateCase> cases = List.of(
                new InvalidCoordinateCase("좌표 누락", null, "37.1"),
                new InvalidCoordinateCase("좌표 형식 오류", "not-a-number", "37.1"),
                new InvalidCoordinateCase("좌표 범위 오류", "181", "37.1")
        );

        for (InvalidCoordinateCase testCase : cases) {
            MerchantUsageData input = createUnclassifiedUsage(testCase.merchantName());
            MerchantClassificationProcessor processor = createProcessor(query -> List.of(
                    new SearchCandidate(
                            "3001",
                            testCase.merchantName(),
                            KakaoPlaceMatchData.RESTAURANT_CATEGORY_CODE,
                            testCase.longitude(),
                            testCase.latitude()
                    )
            ));

            KakaoPlaceMatchData result = processor.classify(List.of(input))
                    .get(0)
                    .kakaoPlaceMatch();

            assertThat(result.longitude()).isNull();
            assertThat(result.latitude()).isNull();
        }
    }

    /**
     * 테스트용 가맹점 사용 이력과 검색 Client를 연결한 실제 분류 컴포넌트를 만든다.
     *
     * @param searchClient 테스트 시나리오에 맞는 카카오 장소 후보를 반환할 Client
     * @return 실제 이름 파싱·점수 계산·후보 선정을 사용하는 분류 컴포넌트
     */
    private MerchantClassificationProcessor createProcessor(
            MerchantPlaceSearchClient searchClient
    ) {
        MerchantNameParser merchantNameParser = new MerchantNameParser();
        MerchantPlaceMatcher merchantPlaceMatcher = new MerchantPlaceMatcher(
                searchClient,
                merchantNameParser,
                new MerchantMatchScoreCalculator(merchantNameParser)
        );
        return new MerchantClassificationProcessor(merchantPlaceMatcher);
    }

    /**
     * 카카오 검색 전 기본값과 점심 결제 한 건을 가진 테스트용 가맹점 사용 이력을 만든다.
     *
     * @param merchantName 검색 후보와 비교할 원본 가맹점명
     * @return 미매칭 카카오 결과를 가진 테스트용 사용 이력
     */
    private MerchantUsageData createUnclassifiedUsage(String merchantName) {
        return MerchantUsageData.unclassified(
                merchantName,
                "211-75-37672",
                TimeBand.LUNCH,
                List.of(new MerchantUsageData.PaymentLog(
                        LocalDateTime.of(2026, 7, 18, 13, 20),
                        BigDecimal.valueOf(11_000)
                ))
        );
    }

    /**
     * 카카오가 회신할 수 있는 좌표 누락·형식·범위 오류 사례를 묶는 테스트 전용 값이다.
     *
     * @param merchantName 각 반복을 구분할 가맹점명
     * @param longitude 카카오 경도 원본 문자열
     * @param latitude 카카오 위도 원본 문자열
     */
    private record InvalidCoordinateCase(
            String merchantName,
            String longitude,
            String latitude
    ) {
    }
}
