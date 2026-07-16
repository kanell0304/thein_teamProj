package com.anything.momeogji.service.recommendation;

import com.anything.momeogji.dto.recommendation.AggregatedCondition;
import com.anything.momeogji.dto.recommendation.CategoryCount;
import com.anything.momeogji.dto.recommendation.CommonOptionRequest;
import com.anything.momeogji.dto.recommendation.RestaurantCandidate;
import com.anything.momeogji.exception.recommendation.AiRecommendationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class RestaurantCandidateSearchServiceImplTest {

    @Mock
    private KakaoLocalClient kakaoLocalClient;

    private RestaurantCandidateSearchServiceImpl service;

    private CommonOptionRequest commonOption;

    @BeforeEach
    void setUp() {
        service = new RestaurantCandidateSearchServiceImpl(kakaoLocalClient);
        commonOption = new CommonOptionRequest(
                "강남역", 37.498, 127.027, LocalDateTime.of(2026, 7, 20, 12, 0), "식사"
        );
    }

    @Test
    void 카테고리별_검색결과를_합쳐서_충분하면_반경을_넓히지_않는다() {
        // averageWalkMinutes=10 -> 반경 700m
        AggregatedCondition condition = condition(10.0, List.of(
                new CategoryCount("한식", 2), new CategoryCount("일식", 1)
        ));
        given(kakaoLocalClient.searchNearby(eq("한식"), eq(127.027), eq(37.498), eq(700), anyInt()))
                .willReturn(candidates("h", 5, 100));
        given(kakaoLocalClient.searchNearby(eq("일식"), eq(127.027), eq(37.498), eq(700), anyInt()))
                .willReturn(candidates("j", 3, 200));

        List<RestaurantCandidate> result = service.search(commonOption, condition, Set.of());

        assertThat(result).hasSize(8);
        verify(kakaoLocalClient, never()).searchNearby(eq("한식"), eq(127.027), eq(37.498), eq(1400), anyInt());
    }

    @Test
    void 초기_풀이_부족하면_반경을_두배로_넓혀_재검색한다() {
        AggregatedCondition condition = condition(10.0, List.of(new CategoryCount("한식", 2)));
        given(kakaoLocalClient.searchNearby(eq("한식"), eq(127.027), eq(37.498), eq(700), anyInt()))
                .willReturn(candidates("h", 3, 100));
        given(kakaoLocalClient.searchNearby(eq("한식"), eq(127.027), eq(37.498), eq(1400), anyInt()))
                .willReturn(candidates("w", 6, 300));

        List<RestaurantCandidate> result = service.search(commonOption, condition, Set.of());

        assertThat(result).hasSize(9);
        verify(kakaoLocalClient).searchNearby(eq("한식"), eq(127.027), eq(37.498), eq(1400), anyInt());
    }

    @Test
    void 반경을_넓혀도_최소_후보수_미만이면_예외를_던진다() {
        AggregatedCondition condition = condition(5.0, List.of(new CategoryCount("한식", 1)));
        given(kakaoLocalClient.searchNearby(eq("한식"), eq(127.027), eq(37.498), anyInt(), anyInt()))
                .willReturn(candidates("h", 2, 100));

        assertThatThrownBy(() -> service.search(commonOption, condition, Set.of()))
                .isInstanceOf(AiRecommendationException.class);
    }

    @Test
    void 선호_카테고리가_없으면_기본_키워드로_검색한다() {
        AggregatedCondition condition = condition(10.0, List.of());
        given(kakaoLocalClient.searchNearby(eq("음식점"), eq(127.027), eq(37.498), eq(700), anyInt()))
                .willReturn(candidates("f", 6, 100));

        List<RestaurantCandidate> result = service.search(commonOption, condition, Set.of());

        assertThat(result).hasSize(6);
    }

    @Test
    void 제외_id로_지정된_후보는_풀에서_빠지고_그로_인해_반경도_확장된다() {
        AggregatedCondition condition = condition(10.0, List.of(new CategoryCount("한식", 2)));
        // h0~h5 6곳 중 4곳을 제외하면 2곳만 남아 반경 확장이 필요해진다.
        given(kakaoLocalClient.searchNearby(eq("한식"), eq(127.027), eq(37.498), eq(700), anyInt()))
                .willReturn(candidates("h", 6, 100));
        given(kakaoLocalClient.searchNearby(eq("한식"), eq(127.027), eq(37.498), eq(1400), anyInt()))
                .willReturn(candidates("w", 5, 300));

        List<RestaurantCandidate> result = service.search(commonOption, condition,
                Set.of("h0", "h1", "h2", "h3"));

        assertThat(result).extracting(RestaurantCandidate::id).doesNotContain("h0", "h1", "h2", "h3");
        verify(kakaoLocalClient).searchNearby(eq("한식"), eq(127.027), eq(37.498), eq(1400), anyInt());
    }

    private AggregatedCondition condition(double averageWalkMinutes, List<CategoryCount> categoryPriority) {
        return new AggregatedCondition(2, averageWalkMinutes, categoryPriority, 15000, 0, Set.of(), List.of());
    }

    private List<RestaurantCandidate> candidates(String prefix, int count, int baseDistance) {
        return java.util.stream.IntStream.range(0, count)
                .mapToObj(i -> new RestaurantCandidate(
                        prefix + i, prefix + "식당" + i, "한식", "도로명주소" + i, "지번주소" + i,
                        37.5 + i * 0.001, 127.0 + i * 0.001, baseDistance + i))
                .toList();
    }
}
