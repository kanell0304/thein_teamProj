package com.anything.momeogji.service.recommendation;

import com.anything.momeogji.dto.recommendation.AggregatedCondition;
import com.anything.momeogji.dto.recommendation.CategoryCount;
import com.anything.momeogji.dto.recommendation.CommonOptionRequest;
import com.anything.momeogji.dto.recommendation.RestaurantCandidate;
import com.anything.momeogji.exception.recommendation.AiRecommendationException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 카카오 키워드 검색으로 실제 음식점 후보 풀을 만든다.
 * AI가 사실을 지어내는 대신, 여기서 만든 실존 후보 목록 안에서만 고르게 하기 위한 전처리 단계.
 */
@Service
@RequiredArgsConstructor
public class RestaurantCandidateSearchServiceImpl implements RestaurantCandidateSearchService {

    private static final int TOP_CATEGORY_COUNT = 3;
    private static final int SEARCH_SIZE_PER_KEYWORD = 15;
    private static final int MIN_POOL_SIZE_BEFORE_EXPANDING = 6;
    private static final int MIN_ACCEPTABLE_POOL_SIZE = 3;
    private static final int MAX_POOL_SIZE = 20;
    private static final int MIN_RADIUS_METERS = 500;
    private static final int MAX_RADIUS_METERS = 20_000;
    private static final double WALK_METERS_PER_MINUTE = 70.0;
    private static final String FALLBACK_KEYWORD = "음식점";

    private final KakaoLocalClient kakaoLocalClient;

    @Override
    public List<RestaurantCandidate> search(CommonOptionRequest commonOption, AggregatedCondition condition, Set<String> excludedCandidateIds) {
        List<String> keywords = topCategories(condition);
        int radius = walkMinutesToRadiusMeters(condition.averageWalkMinutes());

        Map<String, RestaurantCandidate> pool = new LinkedHashMap<>();
        collectCandidates(pool, keywords, commonOption, radius, excludedCandidateIds);

        if (pool.size() < MIN_POOL_SIZE_BEFORE_EXPANDING && radius < MAX_RADIUS_METERS) {
            int widerRadius = Math.min(radius * 2, MAX_RADIUS_METERS);
            collectCandidates(pool, keywords, commonOption, widerRadius, excludedCandidateIds);
        }

        if (pool.size() < MIN_ACCEPTABLE_POOL_SIZE) {
            throw new AiRecommendationException(
                    "조건에 맞는 음식점 후보를 충분히 찾지 못했습니다(검색된 후보 %d곳). 지역이나 조건을 조정해 다시 시도해 주세요."
                            .formatted(pool.size()));
        }

        return pool.values().stream()
                .sorted(Comparator.comparingInt(c -> c.distanceMeters() != null ? c.distanceMeters() : Integer.MAX_VALUE))
                .limit(MAX_POOL_SIZE)
                .toList();
    }

    private void collectCandidates(Map<String, RestaurantCandidate> pool, List<String> keywords,
                                    CommonOptionRequest commonOption, int radiusMeters, Set<String> excludedCandidateIds) {
        for (String keyword : keywords) {
            List<RestaurantCandidate> results = kakaoLocalClient.searchNearby(
                    keyword,
                    commonOption.destinationLongitude(),
                    commonOption.destinationLatitude(),
                    radiusMeters,
                    SEARCH_SIZE_PER_KEYWORD
            );
            for (RestaurantCandidate candidate : results) {
                if (!excludedCandidateIds.contains(candidate.id())) {
                    pool.putIfAbsent(candidate.id(), candidate);
                }
            }
        }
    }

    private List<String> topCategories(AggregatedCondition condition) {
        List<String> categories = condition.categoryPriority().stream()
                .map(CategoryCount::category)
                .limit(TOP_CATEGORY_COUNT)
                .toList();
        return categories.isEmpty() ? List.of(FALLBACK_KEYWORD) : categories;
    }

    private int walkMinutesToRadiusMeters(double averageWalkMinutes) {
        int meters = (int) Math.round(averageWalkMinutes * WALK_METERS_PER_MINUTE);
        return Math.max(MIN_RADIUS_METERS, Math.min(meters, MAX_RADIUS_METERS));
    }
}
