package com.anything.momeogji.service.recommendation;

import com.anything.momeogji.dto.recommendation.AggregatedCondition;
import com.anything.momeogji.dto.recommendation.RecommendationRequest;
import com.anything.momeogji.dto.recommendation.RecommendationResult;
import com.anything.momeogji.dto.recommendation.RestaurantRecommendation;
import com.anything.momeogji.dto.recommendation.Tier;
import com.anything.momeogji.exception.recommendation.AiRecommendationException;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;

 // OpenAI 추천을 호출해 "주 후보 3개(PRIMARY) + 예비 후보 2개(EXTRA)" 형태로 가공하는 핵심 서비스.
 // DB 테이블이 아직 확정되지 않았으므로 결과는 영속화하지 않고 요청-응답으로만 다룬다.

@Service
@RequiredArgsConstructor
public class RestaurantRecommendationServiceImpl implements RestaurantRecommendationService {

    private static final int PRIMARY_COUNT = 3;
    private static final int EXTRA_COUNT = 2;

    private final RecommendationConditionAggregator conditionAggregator;
    private final RecommendationPromptBuilder promptBuilder;
    private final OpenAiChatClient openAiChatClient;
    private final ObjectMapper objectMapper;

    @Override
    public RecommendationResult recommend(RecommendationRequest request) {
        AggregatedCondition condition = conditionAggregator.aggregate(request.personalOptions());

        String systemPrompt = promptBuilder.buildSystemPrompt();
        String userPrompt = promptBuilder.buildUserPrompt(request.commonOption(), condition);
        String rawContent = openAiChatClient.requestStructuredJson(systemPrompt, userPrompt);

        RestaurantListPayload payload = parsePayload(rawContent);
        validatePayload(payload);

        List<RestaurantRecommendation> primary = filterAndSort(payload, Tier.PRIMARY);
        List<RestaurantRecommendation> extra = filterAndSort(payload, Tier.EXTRA);

        return new RecommendationResult(condition.participantCount(), primary, extra);
    }

    private RestaurantListPayload parsePayload(String rawContent) {
        try {
            return objectMapper.readValue(rawContent, RestaurantListPayload.class);
        } catch (JsonProcessingException e) {
            throw new AiRecommendationException("AI 추천 응답을 JSON으로 해석하지 못했습니다: " + rawContent, e);
        }
    }

    private void validatePayload(RestaurantListPayload payload) {
        if (payload == null || payload.restaurants() == null) {
            throw new AiRecommendationException("AI 추천 응답이 비어 있습니다.");
        }
        long primaryCount = payload.restaurants().stream().filter(r -> r.tier() == Tier.PRIMARY).count();
        long extraCount = payload.restaurants().stream().filter(r -> r.tier() == Tier.EXTRA).count();
        if (primaryCount != PRIMARY_COUNT || extraCount != EXTRA_COUNT) {
            throw new AiRecommendationException(
                    "AI 추천 결과 개수가 올바르지 않습니다. (primary=%d, extra=%d, 기대값 primary=%d, extra=%d)"
                            .formatted(primaryCount, extraCount, PRIMARY_COUNT, EXTRA_COUNT));
        }
    }

    private List<RestaurantRecommendation> filterAndSort(RestaurantListPayload payload, Tier tier) {
        return payload.restaurants().stream()
                .filter(r -> r.tier() == tier)
                .sorted(Comparator.comparingInt(RestaurantRecommendation::rank))
                .toList();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record RestaurantListPayload(List<RestaurantRecommendation> restaurants) {
    }
}
