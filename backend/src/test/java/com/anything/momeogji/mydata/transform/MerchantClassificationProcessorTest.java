package com.anything.momeogji.mydata.transform;

import com.anything.momeogji.mydata.transform.local.MerchantPlaceSearchClient;
import com.anything.momeogji.mydata.transform.local.MerchantPlaceSearchClient.SearchCandidate;
import com.anything.momeogji.mydata.transform.model.MerchantUsageData;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link MerchantClassificationProcessor}가 목적 그룹에 맞는 카카오 장소명과 세부 카테고리만 최종 결과에 남기는지 검증한다.
 */
class MerchantClassificationProcessorTest {

    /**
     * 완전히 일치하는 음식점 후보의 장소명과 전체 카테고리 경로를 사용 이력에 결합하는지 확인한다.
     */
    @Test
    void 완전일치_음식점_후보를_최종_결과에_결합한다() {
        MerchantUsageData input = createUnclassifiedUsage("영인성");
        List<String> requestedCategoryCodes = new ArrayList<>();
        MerchantClassificationProcessor processor = createProcessor((query, categoryGroupCode) -> {
            requestedCategoryCodes.add(categoryGroupCode);
            return List.of(new SearchCandidate(
                    "영인성",
                    "FD6",
                    "음식점 > 중식 > 중화요리"
            ));
        });

        List<MerchantUsageData> result = processor.classify(List.of(input), "FD6");

        assertThat(result).hasSize(1);
        MerchantUsageData classified = result.get(0);
        assertThat(classified).isNotSameAs(input);
        assertThat(classified.merchantName()).isEqualTo(input.merchantName());
        assertThat(classified.merchantCode()).isEqualTo(input.merchantCode());
        assertThat(classified.payments()).isEqualTo(input.payments());
        assertThat(classified.kakaoPlaceMatch().placeName()).isEqualTo("영인성");
        assertThat(classified.kakaoPlaceMatch().categoryName())
                .isEqualTo("음식점 > 중식 > 중화요리");
        assertThat(requestedCategoryCodes).containsExactly("FD6");
        assertThatThrownBy(() -> result.add(input))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    /**
     * 원본명 검색과 비교용 이름 재검색에 같은 목적 그룹 코드가 전달되는지 확인한다.
     */
    @Test
    void 두_검색_회차에_동일한_그룹_코드를_전달한다() {
        MerchantUsageData input = createUnclassifiedUsage("강남모밀　무이");
        List<String> requestedCategoryCodes = new ArrayList<>();
        MerchantClassificationProcessor processor = createProcessor((query, categoryGroupCode) -> {
            requestedCategoryCodes.add(categoryGroupCode);
            if ("강남모밀무이".equals(query)) {
                return List.of(new SearchCandidate(
                        "강남모밀 무이",
                        "FD6",
                        "음식점 > 일식 > 국수"
                ));
            }
            return List.of();
        });

        List<MerchantUsageData> result = processor.classify(List.of(input), "FD6");

        assertThat(result).hasSize(1);
        assertThat(requestedCategoryCodes).containsExactly("FD6", "FD6");
    }

    /**
     * 검색 결과가 없거나 장소명 매칭에 실패하면 해당 가맹점을 최종 목록에서 제외하는지 확인한다.
     */
    @Test
    void 검색_결과가_없으면_가맹점을_제외한다() {
        MerchantUsageData input = createUnclassifiedUsage("영인성");
        MerchantClassificationProcessor processor = createProcessor(
                (query, categoryGroupCode) -> List.of()
        );

        assertThat(processor.classify(List.of(input), "FD6")).isEmpty();
    }

    /**
     * 최고 후보의 그룹이 요청과 다르거나 세부 카테고리가 누락되면 결과를 제외하는지 확인한다.
     */
    @Test
    void 그룹_불일치와_세부_카테고리_누락은_제외한다() {
        MerchantUsageData input = createUnclassifiedUsage("영인성");
        List<SearchCandidate> invalidCandidates = List.of(
                new SearchCandidate("영인성", "CE7", "카페 > 커피전문점"),
                new SearchCandidate("영인성", "FD6", null)
        );

        for (SearchCandidate invalidCandidate : invalidCandidates) {
            MerchantClassificationProcessor processor = createProcessor(
                    (query, categoryGroupCode) -> List.of(invalidCandidate)
            );

            assertThat(processor.classify(List.of(input), "FD6")).isEmpty();
        }
    }

    /**
     * 카테고리가 충돌한 동일 우선순위 후보의 최고 이름 유사도가 같으면 결과를 제외하는지 확인한다.
     */
    @Test
    void 카테고리_충돌_후보의_최고_이름_유사도가_같으면_제외한다() {
        MerchantUsageData input = createUnclassifiedUsage("영인성");
        MerchantClassificationProcessor processor = createProcessor(
                (query, categoryGroupCode) -> List.of(
                        new SearchCandidate("영인성", "FD6", "음식점 > 중식 > 중화요리"),
                        new SearchCandidate("영인성", "FD6", "음식점 > 한식 > 백반")
                )
        );

        assertThat(processor.classify(List.of(input), "FD6")).isEmpty();
    }

    /**
     * 동일 우선순위 후보들의 카테고리가 같으면 이름 유사도를 다시 비교하지 않고 첫 후보를 사용하는지 확인한다.
     */
    @Test
    void 동일_우선순위_후보의_카테고리가_같으면_첫_장소를_사용한다() {
        String merchantName = "abcdefghij";
        MerchantUsageData input = createUnclassifiedUsage(merchantName);
        MerchantClassificationProcessor processor = createProcessor(
                (query, categoryGroupCode) -> List.of(
                        new SearchCandidate(
                                merchantName + "xy",
                                "FD6",
                                "음식점 > 중식 > 중화요리"
                        ),
                        new SearchCandidate(
                                merchantName + "x",
                                "FD6",
                                "음식점 > 중식 > 중화요리"
                        )
                )
        );

        MerchantUsageData result = processor.classify(List.of(input), "FD6").get(0);

        assertThat(result.kakaoPlaceMatch().placeName()).isEqualTo(merchantName + "xy");
        assertThat(result.kakaoPlaceMatch().categoryName())
                .isEqualTo("음식점 > 중식 > 중화요리");
    }

    /**
     * 카테고리가 충돌하면 근소한 길이 비율 차이도 허용해 유일하게 가장 유사한 장소를 선택하는지 확인한다.
     */
    @Test
    void 카테고리가_충돌하면_근소한_차이여도_가장_유사한_후보를_선택한다() {
        String merchantName = "가".repeat(100);
        String mostSimilarPlaceName = merchantName + "나";
        MerchantUsageData input = createUnclassifiedUsage(merchantName);
        MerchantClassificationProcessor processor = createProcessor(
                (query, categoryGroupCode) -> List.of(
                        new SearchCandidate(
                                mostSimilarPlaceName,
                                "FD6",
                                "음식점 > 한식 > 백반"
                        ),
                        new SearchCandidate(
                                merchantName + "나다",
                                "FD6",
                                "음식점 > 중식 > 중화요리"
                        )
                )
        );

        MerchantUsageData result = processor.classify(List.of(input), "FD6").get(0);

        assertThat(result.kakaoPlaceMatch().placeName()).isEqualTo(mostSimilarPlaceName);
        assertThat(result.kakaoPlaceMatch().categoryName()).isEqualTo("음식점 > 한식 > 백반");
    }

    /**
     * 세 후보의 카테고리가 충돌해도 최고 길이 비율 후보가 하나면 해당 장소를 선택하는지 확인한다.
     */
    @Test
    void 세_후보_중_최고_이름_유사도가_하나면_해당_후보를_선택한다() {
        String merchantName = "abcdefghij";
        MerchantUsageData input = createUnclassifiedUsage(merchantName);
        MerchantClassificationProcessor processor = createProcessor(
                (query, categoryGroupCode) -> List.of(
                        new SearchCandidate(
                                merchantName + "x",
                                "FD6",
                                "음식점 > 한식 > 백반"
                        ),
                        new SearchCandidate(
                                merchantName + "xy",
                                "FD6",
                                "음식점 > 중식 > 중화요리"
                        ),
                        new SearchCandidate(
                                merchantName + "xyz",
                                "FD6",
                                "음식점 > 일식 > 초밥"
                        )
                )
        );

        MerchantUsageData result = processor.classify(List.of(input), "FD6").get(0);

        assertThat(result.kakaoPlaceMatch().placeName()).isEqualTo(merchantName + "x");
        assertThat(result.kakaoPlaceMatch().categoryName()).isEqualTo("음식점 > 한식 > 백반");
    }

    /**
     * 세 후보 중 최고 이름 유사도 동률이 둘 이상이면 더 낮은 후보로 대체하지 않고 제외하는지 확인한다.
     */
    @Test
    void 세_후보_중_최고_이름_유사도가_동률이면_낮은_후보를_사용하지_않는다() {
        String merchantName = "abcdefghij";
        MerchantUsageData input = createUnclassifiedUsage(merchantName);
        MerchantClassificationProcessor processor = createProcessor(
                (query, categoryGroupCode) -> List.of(
                        new SearchCandidate(
                                merchantName + "x",
                                "FD6",
                                "음식점 > 한식 > 백반"
                        ),
                        new SearchCandidate(
                                "y" + merchantName,
                                "FD6",
                                "음식점 > 중식 > 중화요리"
                        ),
                        new SearchCandidate(
                                merchantName + "zz",
                                "FD6",
                                "음식점 > 일식 > 초밥"
                        )
                )
        );

        assertThat(processor.classify(List.of(input), "FD6")).isEmpty();
    }

    /**
     * 전각 문자와 대소문자 차이가 있어도 정규화된 이름 길이로 카테고리 충돌을 해소하는지 확인한다.
     */
    @Test
    void 정규화된_이름을_기준으로_유사도를_비교한다() {
        MerchantUsageData input = createUnclassifiedUsage("ＡＢＣＤＥＦＧＨＩＪ");
        MerchantClassificationProcessor processor = createProcessor(
                (query, categoryGroupCode) -> List.of(
                        new SearchCandidate(
                                "abcdefghijx",
                                "FD6",
                                "음식점 > 한식 > 백반"
                        ),
                        new SearchCandidate(
                                "ABCDEFGHIJxy",
                                "FD6",
                                "음식점 > 중식 > 중화요리"
                        )
                )
        );

        MerchantUsageData result = processor.classify(List.of(input), "FD6").get(0);

        assertThat(result.kakaoPlaceMatch().placeName()).isEqualTo("abcdefghijx");
        assertThat(result.kakaoPlaceMatch().categoryName()).isEqualTo("음식점 > 한식 > 백반");
    }

    /**
     * 부분일치 길이 비율이 정확히 60%면 포함하고 60% 미만이면 제외하는지 확인한다.
     */
    @Test
    void 부분일치_60퍼센트_경계를_교차곱으로_판정한다() {
        MerchantUsageData input = createUnclassifiedUsage("abc");
        MerchantClassificationProcessor boundaryProcessor = createProcessor(
                (query, categoryGroupCode) -> List.of(new SearchCandidate(
                        "abcde",
                        "FD6",
                        "음식점 > 한식 > 백반"
                ))
        );
        MerchantClassificationProcessor belowBoundaryProcessor = createProcessor(
                (query, categoryGroupCode) -> List.of(new SearchCandidate(
                        "abcdef",
                        "FD6",
                        "음식점 > 한식 > 백반"
                ))
        );

        assertThat(boundaryProcessor.classify(List.of(input), "FD6")).hasSize(1);
        assertThat(belowBoundaryProcessor.classify(List.of(input), "FD6")).isEmpty();
    }

    /**
     * 최종 장소 매칭 모델에는 내부 유사도나 신뢰도 속성이 추가되지 않았는지 확인한다.
     */
    @Test
    void 최종_결과에는_내부_유사도_필드가_노출되지_않는다() {
        MerchantUsageData input = createUnclassifiedUsage("영인성");

        assertThat(Arrays.stream(
                        input.kakaoPlaceMatch().getClass().getRecordComponents()
                ).map(component -> component.getName()))
                .containsExactly("placeName", "categoryName");
    }

    /**
     * 음식점·카페 이외 그룹 코드는 외부 검색 전에 거부하는지 확인한다.
     */
    @Test
    void 허용하지_않는_그룹_코드를_거부한다() {
        MerchantUsageData input = createUnclassifiedUsage("테스트 편의점");
        MerchantClassificationProcessor processor = createProcessor(
                (query, categoryGroupCode) -> List.of()
        );

        assertThatThrownBy(() -> processor.classify(List.of(input), "CS2"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("categoryGroupCode는 FD6 또는 CE7이어야 합니다.");
    }

    /**
     * 테스트용 가맹점 사용 이력과 검색 Client를 연결한 실제 분류 컴포넌트를 만든다.
     *
     * @param searchClient 테스트 시나리오에 맞는 카카오 장소 후보를 반환할 Client
     * @return 실제 이름 정규화·우선순위 판정·후보 선정을 사용하는 분류 컴포넌트
     */
    private MerchantClassificationProcessor createProcessor(
            MerchantPlaceSearchClient searchClient
    ) {
        MerchantNameParser merchantNameParser = new MerchantNameParser();
        MerchantPlaceMatcher merchantPlaceMatcher = new MerchantPlaceMatcher(
                searchClient,
                merchantNameParser
        );
        return new MerchantClassificationProcessor(merchantPlaceMatcher);
    }

    /**
     * 카카오 검색 전 기본값과 점심 결제 한 건을 가진 테스트용 가맹점 사용 이력을 만든다.
     *
     * @param merchantName 검색 후보와 비교할 원본 가맹점명
     * @return 분류 전 카카오 결과를 가진 테스트용 사용 이력
     */
    private MerchantUsageData createUnclassifiedUsage(String merchantName) {
        return MerchantUsageData.unclassified(
                merchantName,
                "211-75-37672",
                List.of(new MerchantUsageData.PaymentLog(
                        LocalDateTime.of(2026, 7, 18, 13, 20),
                        BigDecimal.valueOf(11_000)
                ))
        );
    }
}
