package com.anything.momeogji.service.recommendation;

import com.anything.momeogji.dto.recommendation.AggregatedCondition;
import com.anything.momeogji.dto.recommendation.RecommendationRequest;
import com.anything.momeogji.dto.recommendation.RecommendationResult;
import com.anything.momeogji.dto.recommendation.RestaurantCandidate;
import com.anything.momeogji.dto.recommendation.RestaurantRecommendation;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;
import java.util.stream.IntStream;

// RestaurantRecommendationService의 두 번째 구현체. OpenAI를 호출하지 않고 카카오 후보 검색 결과 중
// 앞에서부터 3곳을 그대로 추천으로 돌려준다 - AI 비용 없이 로컬에서 추천 이후 흐름(투표/공지 등)을
// 빠르게 확인하고 싶을 때 쓴다.
//
// @Profile로 존재 자체를 껐다 켰다 하는 대신, 이 빈은 RestaurantRecommendationServiceImpl(@Primary)과
// 항상 둘 다 등록돼 있다. 기본 주입은 자동으로 @Primary 쪽으로 가고, 이 Mock을 실제로 쓰려면 주입
// 지점에서 @Qualifier("mockRestaurantRecommendationServiceImpl")로 명시적으로 지정해야 한다.
@Service
@RequiredArgsConstructor
public class MockRestaurantRecommendationServiceImpl implements RestaurantRecommendationService {

    private static final Logger log = LoggerFactory.getLogger(MockRestaurantRecommendationServiceImpl.class);
    private static final int RECOMMENDATION_COUNT = 3;
    private static final String MOCK_REASON = "목업 추천 결과입니다 (@Qualifier로 명시 주입된 Mock, AI 미사용)";

    private final RecommendationConditionAggregator conditionAggregator;
    private final RestaurantCandidateSearchService candidateSearchService;

    @Override
    public RecommendationResult recommend(RecommendationRequest request) {
        AggregatedCondition condition = conditionAggregator.aggregate(
                request.personalOptions(), request.myDataRestaurants(), request.commonOption().purpose());
        log.info("[mock] 추천 조건 집계 결과: categoryPriority={}", condition.categoryPriority());

        Set<String> excludedCandidateIds = Set.copyOf(request.excludedRestaurantIds());
        List<RestaurantCandidate> candidates = candidateSearchService.search(request.commonOption(), condition, excludedCandidateIds);

        List<RestaurantRecommendation> recommendations = IntStream.range(0, Math.min(RECOMMENDATION_COUNT, candidates.size()))
                .mapToObj(index -> toRecommendation(candidates.get(index), index + 1))
                .toList();

        return new RecommendationResult(condition.participantCount(), recommendations);
    }

    private RestaurantRecommendation toRecommendation(RestaurantCandidate candidate, int rank) {
        return new RestaurantRecommendation(
                candidate.id(),
                rank,
                candidate.name(),
                candidate.category(),
                candidate.roadAddress(),
                candidate.address(),
                candidate.latitude(),
                candidate.longitude(),
                MOCK_REASON,
                null
        );
    }
}
