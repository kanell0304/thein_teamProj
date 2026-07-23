package com.anything.momeogji.service.recommendation;

import com.anything.momeogji.dto.recommendation.AggregatedCondition;
import com.anything.momeogji.dto.recommendation.CategoryCount;
import com.anything.momeogji.dto.recommendation.PersonalOptionRequest;
import com.anything.momeogji.mydata.processing.model.MyDataRestaurantData;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 개인 옵션 리스트를 AI 프롬프트에 실을 그룹 단위 신호로 집계한다.
 * "AI 옵션 정의" 문서의 처리 기준을 그대로 코드로 옮긴 것:
 * - 도보 가능 거리 → 평균값
 * - 선호 카테고리 → 중복 횟수별 우선순위 (참여자가 직접 고른 카테고리 + MyData로 확인된 방문 카테고리)
 * - 지출 가능 금액 한도 → 최저금액 기준 우선순위
 */
@Component
public class RecommendationConditionAggregator {

    public AggregatedCondition aggregate(List<PersonalOptionRequest> personalOptions,
                                          List<MyDataRestaurantData> myDataRestaurants) {
        if (personalOptions == null || personalOptions.isEmpty()) {
            throw new IllegalArgumentException("개인 옵션 데이터가 최소 1건 이상 필요합니다.");
        }

        int participantCount = personalOptions.size();

        double averageWalkMinutes = personalOptions.stream()
                .mapToInt(PersonalOptionRequest::walkMinutes)
                .average()
                .orElse(0);

        // 참여자가 직접 고른 카테고리와, MyData에서 확인된 방문 이력의 카테고리를 같은 자격으로 합산한다 -
        // 방문 기록 하나하나가 직접 선택과 동일하게 1표씩으로 취급된다.
        Stream<String> manualCategoryVotes = personalOptions.stream()
                .flatMap(option -> option.preferredCategories().stream());
        Stream<String> myDataCategoryVotes = (myDataRestaurants == null ? List.<MyDataRestaurantData>of() : myDataRestaurants)
                .stream()
                .map(MyDataRestaurantData::foodCategory)
                .map(MyDataCategoryMatcher::match)
                .filter(Objects::nonNull);

        List<CategoryCount> categoryPriority = Stream.concat(manualCategoryVotes, myDataCategoryVotes)
                .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()))
                .entrySet().stream()
                .map(entry -> new CategoryCount(entry.getKey(), entry.getValue()))
                .sorted(Comparator.comparingLong(CategoryCount::count).reversed())
                .toList();

        Integer minBudget = personalOptions.stream()
                .map(PersonalOptionRequest::budgetLimit)
                .filter(Objects::nonNull)
                .min(Integer::compareTo)
                .orElse(null);

        long parkingRequiredCount = personalOptions.stream()
                .filter(PersonalOptionRequest::parkingNeeded)
                .count();

        Set<String> excludedFoods = personalOptions.stream()
                .flatMap(option -> option.excludedFoods().stream())
                .collect(Collectors.toCollection(LinkedHashSet::new));

        List<String> atmospherePriority = personalOptions.stream()
                .map(PersonalOptionRequest::atmosphere)
                .filter(Objects::nonNull)
                .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()))
                .entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .map(Map.Entry::getKey)
                .toList();

        return new AggregatedCondition(
                participantCount,
                averageWalkMinutes,
                categoryPriority,
                minBudget,
                parkingRequiredCount,
                excludedFoods,
                atmospherePriority
        );
    }
}
