package com.anything.momeogji.service.recommendation;

import com.anything.momeogji.dto.recommendation.RecommendationRequest;
import com.anything.momeogji.dto.recommendation.RecommendationResult;

public interface RestaurantRecommendationService {

    RecommendationResult recommend(RecommendationRequest request);
}
