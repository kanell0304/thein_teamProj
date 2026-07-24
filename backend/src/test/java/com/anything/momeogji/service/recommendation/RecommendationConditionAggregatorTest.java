package com.anything.momeogji.service.recommendation;

import com.anything.momeogji.dto.recommendation.AggregatedCondition;
import com.anything.momeogji.dto.recommendation.CategoryCount;
import com.anything.momeogji.dto.recommendation.PersonalOptionRequest;
import com.anything.momeogji.mydata.processing.model.MyDataRestaurantData;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
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

        AggregatedCondition condition = aggregator.aggregate(options, List.of(), null);

        assertThat(condition.participantCount()).isEqualTo(3);
        assertThat(condition.averageWalkMinutes()).isEqualTo(10.0);
        assertThat(condition.minBudget()).isEqualTo(10000);
        assertThat(condition.parkingRequiredCount()).isEqualTo(1);
        assertThat(condition.excludedFoods()).containsExactly("고수");
    }

    @Test
    void MyData가_없으면_그날_선택만으로_카테고리_우선순위를_정한다() {
        List<PersonalOptionRequest> options = List.of(
                new PersonalOptionRequest(1L, 5, List.of("한식"), null, false, List.of(), null),
                new PersonalOptionRequest(2L, 5, List.of("한식", "일식"), null, false, List.of(), null),
                new PersonalOptionRequest(3L, 5, List.of("일식"), null, false, List.of(), null)
        );

        AggregatedCondition condition = aggregator.aggregate(options, List.of(), null);

        assertThat(condition.categoryPriority())
                .extracting(CategoryCount::category)
                .containsExactlyInAnyOrder("한식", "일식");
        // MyData 묶음이 없으므로 그날 선택 묶음(한식 2표, 일식 2표)을 그대로 정규화 - 둘 다 동률(500).
        assertThat(condition.categoryPriority()).extracting(CategoryCount::count).containsExactly(500L, 500L);
    }

    @Test
    void 개인_옵션이_비어있으면_예외() {
        assertThatThrownBy(() -> aggregator.aggregate(List.of(), List.of(), null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 그날_선택과_MyData가_모두_있으면_50대_50으로_섞는다() {
        // 그날 선택: 한식 100%(참여자 전원 한식). MyData: 한식 9건 중 일부 없이 전부 일식이라고 하자 -> 일식 100%.
        List<PersonalOptionRequest> options = List.of(
                new PersonalOptionRequest(1L, 5, List.of("한식"), null, false, List.of(), null)
        );
        List<MyDataRestaurantData> myDataRestaurants = List.of(
                new MyDataRestaurantData("가게1", "일식"),
                new MyDataRestaurantData("가게2", "일식"),
                new MyDataRestaurantData("가게3", "일식")
        );

        AggregatedCondition condition = aggregator.aggregate(options, myDataRestaurants, null);

        // 한식(그날 선택 100%)과 일식(MyData 100%)이 50:50으로 섞여 정확히 동률이어야 한다 - MyData가 3건이든 30건이든 동일해야 한다.
        assertThat(condition.categoryPriority())
                .extracting(CategoryCount::category, CategoryCount::count)
                .containsExactlyInAnyOrder(
                        org.assertj.core.groups.Tuple.tuple("한식", 500L),
                        org.assertj.core.groups.Tuple.tuple("일식", 500L)
                );
    }

    @Test
    void MyData_건수가_아무리_많아져도_그날_선택과의_비율은_항상_50대_50이다() {
        List<PersonalOptionRequest> options = List.of(
                new PersonalOptionRequest(1L, 5, List.of("한식"), null, false, List.of(), null)
        );
        List<MyDataRestaurantData> manyMyDataRestaurants = new ArrayList<>();
        for (int i = 0; i < 200; i++) {
            manyMyDataRestaurants.add(new MyDataRestaurantData("가게" + i, "일식"));
        }

        AggregatedCondition condition = aggregator.aggregate(options, manyMyDataRestaurants, null);

        // MyData가 3건이든 200건이든 "일식 100%" 묶음인 건 똑같으므로 결과는 위 테스트와 동일하게 50:50이어야 한다.
        assertThat(condition.categoryPriority())
                .extracting(CategoryCount::category, CategoryCount::count)
                .containsExactlyInAnyOrder(
                        org.assertj.core.groups.Tuple.tuple("한식", 500L),
                        org.assertj.core.groups.Tuple.tuple("일식", 500L)
                );
    }

    @Test
    void 그날_선택된_카테고리가_없으면_MyData를_100퍼센트_사용한다() {
        // 모임 목적이 특정 카테고리로 대응되지 않고("식사"), 참여자 preferredCategories도 매칭 안 되는 값만 있다고 가정.
        // (aggregate 자체는 personalOptions가 항상 최소 1건 이상이지만, 카테고리 값 자체는 비어있을 수 없으므로
        //  이 테스트는 "matcher가 아무것도 못 찾는" MyData 쪽이 아니라 explicitVotes가 실제로 비는 경우를 직접 검증한다)
        List<PersonalOptionRequest> options = List.of(
                new PersonalOptionRequest(1L, 5, List.of(), null, false, List.of(), null)
        );
        List<MyDataRestaurantData> myDataRestaurants = List.of(
                new MyDataRestaurantData("가게1", "한식"),
                new MyDataRestaurantData("가게2", "한식"),
                new MyDataRestaurantData("가게3", "일식")
        );

        AggregatedCondition condition = aggregator.aggregate(options, myDataRestaurants, "식사");

        assertThat(condition.categoryPriority())
                .extracting(CategoryCount::category, CategoryCount::count)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("한식", 667L),
                        org.assertj.core.groups.Tuple.tuple("일식", 333L)
                );
    }

    @Test
    void MyData가_없으면_그날_선택만_100퍼센트_사용한다() {
        List<PersonalOptionRequest> options = List.of(
                new PersonalOptionRequest(1L, 5, List.of("한식"), null, false, List.of(), null)
        );

        AggregatedCondition condition = aggregator.aggregate(options, List.of(), null);

        assertThat(condition.categoryPriority())
                .extracting(CategoryCount::category, CategoryCount::count)
                .containsExactly(org.assertj.core.groups.Tuple.tuple("한식", 1000L));
    }

    @Test
    void 매칭되지_않는_MyData_카테고리는_조용히_무시된다() {
        List<PersonalOptionRequest> options = List.of(
                new PersonalOptionRequest(1L, 5, List.of("한식"), null, false, List.of(), null)
        );
        List<MyDataRestaurantData> myDataRestaurants = List.of(
                new MyDataRestaurantData("알수없는가게", "편의점 > 즉석식품")
        );

        // MyData 쪽에 매칭되는 카테고리가 하나도 없으면 myDataVotes가 비어, 그날 선택만 100% 사용된다.
        AggregatedCondition condition = aggregator.aggregate(options, myDataRestaurants, null);

        assertThat(condition.categoryPriority())
                .extracting(CategoryCount::category, CategoryCount::count)
                .containsExactly(org.assertj.core.groups.Tuple.tuple("한식", 1000L));
    }

    @Test
    void 모임_목적이_카페면_카페_디저트_카테고리도_그날_선택_묶음에_들어간다() {
        List<PersonalOptionRequest> options = List.of(
                new PersonalOptionRequest(1L, 5, List.of("한식"), null, false, List.of(), null)
        );

        AggregatedCondition condition = aggregator.aggregate(options, List.of(), "카페");

        assertThat(condition.categoryPriority())
                .extracting(CategoryCount::category, CategoryCount::count)
                .containsExactlyInAnyOrder(
                        org.assertj.core.groups.Tuple.tuple("한식", 500L),
                        org.assertj.core.groups.Tuple.tuple("카페/디저트", 500L)
                );
    }

    @Test
    void 특정_카테고리로_대응되지_않는_모임_목적은_무시된다() {
        List<PersonalOptionRequest> options = List.of(
                new PersonalOptionRequest(1L, 5, List.of("한식"), null, false, List.of(), null)
        );

        AggregatedCondition condition = aggregator.aggregate(options, List.of(), "식사");

        assertThat(condition.categoryPriority())
                .extracting(CategoryCount::category)
                .containsExactly("한식");
    }
}
