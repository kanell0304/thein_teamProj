package com.anything.momeogji.service.recommendation;

import com.anything.momeogji.dto.recommendation.AggregatedCondition;
import com.anything.momeogji.dto.recommendation.CommonOptionRequest;
import com.anything.momeogji.dto.recommendation.RestaurantCandidate;

import java.util.List;
import java.util.Set;

public interface RestaurantCandidateSearchService {

    /**
     * 목적지 좌표·도보 가능 거리·선호 카테고리를 기준으로 실제 음식점 후보 목록을 검색한다.
     * excludedCandidateIds에 담긴 id는 후보 풀에서 아예 제외한다(재추천 시 이전 추천 제외용).
     * 후보가 너무 적으면(5곳 미만) AiRecommendationException을 던진다.
     */
    List<RestaurantCandidate> search(CommonOptionRequest commonOption, AggregatedCondition condition, Set<String> excludedCandidateIds);
}
