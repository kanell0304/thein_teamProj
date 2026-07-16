package com.anything.momeogji.dto.recommendation;

import java.util.List;
import java.util.Set;

/**
 * 개인 옵션 리스트를 집계한 결과. 참여자 개개인의 원본 데이터는 그대로 AI에 전달하지 않고,
 * 이 집계값(그룹 단위 신호)만 프롬프트에 실어 특정 참여자의 취향이 직접 노출되지 않도록 한다.
 */
public record AggregatedCondition(
        int participantCount,
        double averageWalkMinutes,
        List<CategoryCount> categoryPriority,
        Integer minBudget,
        long parkingRequiredCount,
        Set<String> excludedFoods,
        List<String> atmospherePriority
) {
}
