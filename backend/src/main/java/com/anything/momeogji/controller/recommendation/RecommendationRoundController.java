package com.anything.momeogji.controller.recommendation;

import com.anything.momeogji.dto.recommendation.RoundCreateRequest;
import com.anything.momeogji.dto.recommendation.RoundResponse;
import com.anything.momeogji.service.recommendation.RecommendationRoundService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Recommendation Round [추천 회차]")
@RestController
@RequestMapping("/api/meetups/{meetupId}/rounds")
@RequiredArgsConstructor
public class RecommendationRoundController {

    private final RecommendationRoundService recommendationRoundService;

    /** 최초 추천/재추천 모두 이 엔드포인트로 처리한다. 진행 상황은 채팅방에 웹소켓으로 실시간 브로드캐스트된다. */
    @PostMapping
    public RoundResponse createRound(@PathVariable Long meetupId,
                                      @Valid @RequestBody RoundCreateRequest request,
                                      Authentication authentication) {
        return recommendationRoundService.createRound(meetupId, request, (Long) authentication.getPrincipal());
    }
}
