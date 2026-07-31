package com.anything.momeogji.service.recommendation;

import com.anything.momeogji.dto.recommendation.CommonOptionRequest;
import com.anything.momeogji.dto.recommendation.PersonalOptionRequest;
import com.anything.momeogji.dto.recommendation.RecommendationRequest;
import com.anything.momeogji.dto.recommendation.RecommendationResult;
import com.anything.momeogji.dto.recommendation.RestaurantCandidate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

/** OpenAI를 호출하지 않고 후보 검색 결과 앞에서부터 그대로 추천을 채우는지 확인한다. */
@ExtendWith(MockitoExtension.class)
class MockRestaurantRecommendationServiceImplTest {

    private static final List<RestaurantCandidate> CANDIDATES = List.of(
            new RestaurantCandidate("c1", "모먹지 김밥천국", "한식", "서울 강남구 테헤란로 1", "서울 강남구 역삼동 1", 37.499, 127.028, 100),
            new RestaurantCandidate("c2", "스시모먹지", "일식", "서울 강남구 테헤란로 2", "서울 강남구 역삼동 2", 37.500, 127.029, 150),
            new RestaurantCandidate("c3", "모먹지 삼겹살", "한식", "서울 강남구 테헤란로 3", "서울 강남구 역삼동 3", 37.501, 127.030, 200),
            new RestaurantCandidate("c4", "모먹지 냉면", "한식", "서울 강남구 테헤란로 4", "서울 강남구 역삼동 4", 37.502, 127.031, 250)
    );

    @Mock
    private RestaurantCandidateSearchService candidateSearchService;

    private MockRestaurantRecommendationServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new MockRestaurantRecommendationServiceImpl(
                new RecommendationConditionAggregator(),
                candidateSearchService
        );
    }

    @Test
    void 후보_중_앞에서부터_3곳을_순위대로_추천으로_채운다() {
        given(candidateSearchService.search(any(), any(), any())).willReturn(CANDIDATES);

        RecommendationResult result = service.recommend(sampleRequest());

        assertThat(result.recommendations()).hasSize(3);
        assertThat(result.recommendations().get(0).id()).isEqualTo("c1");
        assertThat(result.recommendations().get(0).rank()).isEqualTo(1);
        assertThat(result.recommendations().get(2).id()).isEqualTo("c3");
        assertThat(result.recommendations()).allSatisfy(recommendation ->
                assertThat(recommendation.imageUrl()).isNull());
    }

    @Test
    void 후보가_3곳보다_적으면_있는_만큼만_추천한다() {
        given(candidateSearchService.search(any(), any(), any())).willReturn(CANDIDATES.subList(0, 2));

        RecommendationResult result = service.recommend(sampleRequest());

        assertThat(result.recommendations()).hasSize(2);
    }

    private RecommendationRequest sampleRequest() {
        CommonOptionRequest common = new CommonOptionRequest(
                "강남역", 37.498, 127.027, LocalDateTime.of(2026, 7, 20, 12, 0), "식사"
        );
        List<PersonalOptionRequest> personal = List.of(
                new PersonalOptionRequest(1L, 5, List.of("한식"), 15000, false, List.of(), "룸")
        );
        return new RecommendationRequest(common, personal, List.of(), List.of(), null);
    }
}
