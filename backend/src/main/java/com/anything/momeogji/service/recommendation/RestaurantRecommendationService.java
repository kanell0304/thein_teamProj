package com.anything.momeogji.service.recommendation;

import com.anything.momeogji.dto.recommendation.RecommendationRequest;
import com.anything.momeogji.dto.recommendation.RecommendationResult;

public interface RestaurantRecommendationService {

    // 음식점 추천 결과
    RecommendationResult recommend(RecommendationRequest request);
}
