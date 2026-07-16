package com.anything.momeogji.controller.recommendation;

import com.anything.momeogji.dto.recommendation.RecommendationRequest;
import com.anything.momeogji.dto.recommendation.RecommendationResult;
import com.anything.momeogji.service.recommendation.RestaurantRecommendationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/ai/recommendations")
@RequiredArgsConstructor
public class RecommendationController {

    private final RestaurantRecommendationService restaurantRecommendationService;

    /** 공통 옵션 + 개인 옵션 리스트를 받아 AI 추천 3(PRIMARY)+2(EXTRA)곳을 반환한다. */
    @PostMapping
    public RecommendationResult recommend(@Valid @RequestBody RecommendationRequest request) {
        return restaurantRecommendationService.recommend(request);
    }
}
