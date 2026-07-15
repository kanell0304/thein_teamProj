package com.anything.momeogji.service.recommendation;

import com.anything.momeogji.dto.recommendation.AggregatedCondition;
import com.anything.momeogji.dto.recommendation.CategoryCount;
import com.anything.momeogji.dto.recommendation.PersonalOptionRequest;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RecommendationConditionAggregatorTest {

    private final RecommendationConditionAggregator aggregator = new RecommendationConditionAggregator();

    @Test
    void 참여자_수와_평균_도보시간과_최저예산을_계산한다() {
        List<PersonalOptionRequest> options = List.of(
                new PersonalOptionRequest("u1", 5, List.of("한식"), 15000, false, List.of(), "룸"),
                new PersonalOptionRequest("u2", 10, List.of("한식", "일식"), 10000, true, List.of("고수"), "개방형"),
                new PersonalOptionRequest("u3", 15, List.of("일식"), null, false, List.of(), null)
        );

        AggregatedCondition condition = aggregator.aggregate(options);

        assertThat(condition.participantCount()).isEqualTo(3);
        assertThat(condition.averageWalkMinutes()).isEqualTo(10.0);
        assertThat(condition.minBudget()).isEqualTo(10000);
        assertThat(condition.parkingRequiredCount()).isEqualTo(1);
        assertThat(condition.excludedFoods()).containsExactly("고수");
    }

    @Test
    void 선호_카테고리는_선택_횟수_내림차순으로_정렬한다() {
        List<PersonalOptionRequest> options = List.of(
                new PersonalOptionRequest("u1", 5, List.of("한식"), null, false, List.of(), null),
                new PersonalOptionRequest("u2", 5, List.of("한식", "일식"), null, false, List.of(), null),
                new PersonalOptionRequest("u3", 5, List.of("일식"), null, false, List.of(), null)
        );

        AggregatedCondition condition = aggregator.aggregate(options);

        assertThat(condition.categoryPriority())
                .extracting(CategoryCount::category)
                .containsExactlyInAnyOrder("한식", "일식");
        assertThat(condition.categoryPriority().get(0).count()).isEqualTo(2);
    }

    @Test
    void 개인_옵션이_비어있으면_예외() {
        assertThatThrownBy(() -> aggregator.aggregate(List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
