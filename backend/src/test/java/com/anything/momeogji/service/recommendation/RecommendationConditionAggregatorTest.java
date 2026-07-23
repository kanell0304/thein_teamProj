package com.anything.momeogji.service.recommendation;

import com.anything.momeogji.dto.recommendation.AggregatedCondition;
import com.anything.momeogji.dto.recommendation.CategoryCount;
import com.anything.momeogji.dto.recommendation.PersonalOptionRequest;
import com.anything.momeogji.mydata.processing.model.MyDataRestaurantData;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RecommendationConditionAggregatorTest {

    private final RecommendationConditionAggregator aggregator = new RecommendationConditionAggregator();

    @Test
    void 참여자_수와_평균_도보시간과_최저예산을_계산한다() {
        List<PersonalOptionRequest> options = List.of(
                new PersonalOptionRequest(1L, 5, List.of("한식"), 15000, false, List.of(), "룸"),
                new PersonalOptionRequest(2L, 10, List.of("한식", "일식"), 10000, true, List.of("고수"), "개방형"),
                new PersonalOptionRequest(3L, 15, List.of("일식"), null, false, List.of(), null)
        );

        AggregatedCondition condition = aggregator.aggregate(options, List.of());

        assertThat(condition.participantCount()).isEqualTo(3);
        assertThat(condition.averageWalkMinutes()).isEqualTo(10.0);
        assertThat(condition.minBudget()).isEqualTo(10000);
        assertThat(condition.parkingRequiredCount()).isEqualTo(1);
        assertThat(condition.excludedFoods()).containsExactly("고수");
    }

    @Test
    void 선호_카테고리는_선택_횟수_내림차순으로_정렬한다() {
        List<PersonalOptionRequest> options = List.of(
                new PersonalOptionRequest(1L, 5, List.of("한식"), null, false, List.of(), null),
                new PersonalOptionRequest(2L, 5, List.of("한식", "일식"), null, false, List.of(), null),
                new PersonalOptionRequest(3L, 5, List.of("일식"), null, false, List.of(), null)
        );

        AggregatedCondition condition = aggregator.aggregate(options, List.of());

        assertThat(condition.categoryPriority())
                .extracting(CategoryCount::category)
                .containsExactlyInAnyOrder("한식", "일식");
        assertThat(condition.categoryPriority().get(0).count()).isEqualTo(2);
    }

    @Test
    void 개인_옵션이_비어있으면_예외() {
        assertThatThrownBy(() -> aggregator.aggregate(List.of(), List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void MyData_방문_카테고리가_선호_카테고리에_한_표씩_더해진다() {
        List<PersonalOptionRequest> options = List.of(
                new PersonalOptionRequest(1L, 5, List.of("한식"), null, false, List.of(), null)
        );
        List<MyDataRestaurantData> myDataRestaurants = List.of(
                new MyDataRestaurantData("영인성", "중식 > 중화요리"),
                new MyDataRestaurantData("상무초밥", "일식 > 초밥")
        );

        AggregatedCondition condition = aggregator.aggregate(options, myDataRestaurants);

        assertThat(condition.categoryPriority())
                .extracting(CategoryCount::category)
                .containsExactlyInAnyOrder("한식", "중식", "일식");
    }

    @Test
    void 매칭되지_않는_MyData_카테고리는_조용히_무시된다() {
        List<PersonalOptionRequest> options = List.of(
                new PersonalOptionRequest(1L, 5, List.of("한식"), null, false, List.of(), null)
        );
        List<MyDataRestaurantData> myDataRestaurants = List.of(
                new MyDataRestaurantData("알수없는가게", "편의점 > 즉석식품")
        );

        AggregatedCondition condition = aggregator.aggregate(options, myDataRestaurants);

        assertThat(condition.categoryPriority())
                .extracting(CategoryCount::category)
                .containsExactly("한식");
    }
}
