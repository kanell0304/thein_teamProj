package com.anything.momeogji.service.recommendation;

import com.anything.momeogji.dto.recommendation.AggregatedCondition;
import com.anything.momeogji.dto.recommendation.CategoryCount;
import com.anything.momeogji.dto.recommendation.PersonalOptionRequest;
import com.anything.momeogji.mydata.processing.model.MyDataRestaurantData;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 개인 옵션 리스트를 AI 프롬프트에 실을 그룹 단위 신호로 집계한다.
 * "AI 옵션 정의" 문서의 처리 기준을 그대로 코드로 옮긴 것:
 * - 도보 가능 거리 → 평균값
 * - 선호 카테고리 → 그날 채팅에서 선택된 카테고리 묶음과 MyData 묶음을 각각 정규화한 뒤 50:50으로 섞은
 *   우선순위. 배수 가중치와 달리 MyData 건수가 아무리 쌓여도 묶음 하나 이상으로는 못 커지므로,
 *   결제 데이터가 누적될수록 항상 그쪽 위주로만 추천되는 문제를 원천 차단한다.
 * - 지출 가능 금액 한도 → 최저금액 기준 우선순위
 */
@Component
public class RecommendationConditionAggregator {

    // 그날 채팅에서 선택된 카테고리 묶음과 MyData 묶음을 이 비율로 섞는다.
    private static final double EXPLICIT_CATEGORY_WEIGHT = 0.5;
    private static final double MYDATA_CATEGORY_WEIGHT = 0.5;
    // CategoryCount.count가 long이라, 정규화된 비중(0~1)을 정수로 보존하기 위한 배율이다.
    private static final long CATEGORY_SCORE_SCALE = 1000L;

    // 호스트가 고르는 모임 목적(THEMES) 중 특정 음식 카테고리로 명확히 대응되는 것만 매핑한다.
    private static final Map<String, String> CATEGORY_BY_MEETING_PURPOSE = Map.of(
            "카페", "카페/디저트",
            "디저트", "카페/디저트"
    );

    public AggregatedCondition aggregate(List<PersonalOptionRequest> personalOptions,
                                          List<MyDataRestaurantData> myDataRestaurants,
                                          String meetingPurpose) {
        if (personalOptions == null || personalOptions.isEmpty()) {
            throw new IllegalArgumentException("개인 옵션 데이터가 최소 1건 이상 필요합니다.");
        }

        int participantCount = personalOptions.size();

        double averageWalkMinutes = personalOptions.stream()
                .mapToInt(PersonalOptionRequest::walkMinutes)
                .average()
                .orElse(0);

        // 묶음 A: 그날 채팅에서 선택된 카테고리(참여자 직접 선택 + 모임 목적).
        Map<String, Long> explicitVotes = new LinkedHashMap<>();
        personalOptions.stream()
                .flatMap(option -> option.preferredCategories().stream())
                .forEach(category -> explicitVotes.merge(category, 1L, Long::sum));

        String purposeCategory = meetingPurpose == null ? null : CATEGORY_BY_MEETING_PURPOSE.get(meetingPurpose);
        if (purposeCategory != null) {
            explicitVotes.merge(purposeCategory, 1L, Long::sum);
        }

        // 묶음 B: MyData 방문 이력.
        Map<String, Long> myDataVotes = new LinkedHashMap<>();
        (myDataRestaurants == null ? List.<MyDataRestaurantData>of() : myDataRestaurants).stream()
                .map(MyDataRestaurantData::foodCategory)
                .map(MyDataCategoryMatcher::match)
                .filter(Objects::nonNull)
                .forEach(category -> myDataVotes.merge(category, 1L, Long::sum));

        List<CategoryCount> categoryPriority = blendCategoryVotes(explicitVotes, myDataVotes);

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

    /**
     * 그날 채팅에서 선택된 카테고리(explicitVotes)와 MyData 방문 이력(myDataVotes)을 각각 하나의 묶음으로
     * 정규화한 뒤 50:50으로 섞는다. 그날 선택된 카테고리가 전혀 없으면(예: 전원 "아무거나") MyData 묶음을
     * 100% 그대로 사용하고, 반대로 MyData가 없으면(미동의·수집 실패 등) 그날 선택만 100% 사용한다.
     */
    private List<CategoryCount> blendCategoryVotes(Map<String, Long> explicitVotes, Map<String, Long> myDataVotes) {
        if (explicitVotes.isEmpty()) {
            return toCategoryPriority(normalize(myDataVotes));
        }
        if (myDataVotes.isEmpty()) {
            return toCategoryPriority(normalize(explicitVotes));
        }

        Map<String, Double> normalizedExplicit = normalize(explicitVotes);
        Map<String, Double> normalizedMyData = normalize(myDataVotes);

        Set<String> categories = new LinkedHashSet<>();
        categories.addAll(normalizedExplicit.keySet());
        categories.addAll(normalizedMyData.keySet());

        Map<String, Double> blended = new LinkedHashMap<>();
        for (String category : categories) {
            double score = EXPLICIT_CATEGORY_WEIGHT * normalizedExplicit.getOrDefault(category, 0.0)
                    + MYDATA_CATEGORY_WEIGHT * normalizedMyData.getOrDefault(category, 0.0);
            blended.put(category, score);
        }
        return toCategoryPriority(blended);
    }

    /** 묶음 내 각 카테고리 표를 전체 합으로 나눠, 합이 1.0인 비중으로 바꾼다. */
    private Map<String, Double> normalize(Map<String, Long> votes) {
        long total = votes.values().stream().mapToLong(Long::longValue).sum();
        Map<String, Double> normalized = new LinkedHashMap<>();
        votes.forEach((category, count) -> normalized.put(category, (double) count / total));
        return normalized;
    }

    private List<CategoryCount> toCategoryPriority(Map<String, Double> scores) {
        return scores.entrySet().stream()
                .map(entry -> new CategoryCount(entry.getKey(), Math.round(entry.getValue() * CATEGORY_SCORE_SCALE)))
                .sorted(Comparator.comparingLong(CategoryCount::count).reversed())
                .toList();
    }
}
