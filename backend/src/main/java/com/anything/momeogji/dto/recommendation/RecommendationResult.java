package com.anything.momeogji.dto.recommendation;

import java.util.List;

public record RecommendationResult(
        int participantCount,
        List<RestaurantRecommendation> primaryRecommendations,
        List<RestaurantRecommendation> extraRecommendations
) {
}
