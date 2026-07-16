package com.anything.momeogji.service.recommendation;

import com.anything.momeogji.dto.recommendation.AggregatedCondition;
import com.anything.momeogji.dto.recommendation.RecommendationRequest;
import com.anything.momeogji.dto.recommendation.RecommendationResult;
import com.anything.momeogji.dto.recommendation.RestaurantCandidate;
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
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

 // OpenAI 추천을 호출해 "주 후보 3개(PRIMARY) + 예비 후보 2개(EXTRA)" 형태로 가공하는 핵심 서비스.
 // 카카오에서 검색한 실제 음식점 후보(candidates) 중에서만 AI가 고르게 하고, 이름/주소/좌표는 항상
 // 그 실검색 데이터에서 채워 넣는다(AI가 직접 만든 값은 신뢰하지 않음).
 // DB 테이블이 아직 확정되지 않았으므로 결과는 영속화하지 않고 요청-응답으로만 다룬다.

@Service
@RequiredArgsConstructor
public class RestaurantRecommendationServiceImpl implements RestaurantRecommendationService {

    private static final int PRIMARY_COUNT = 3;
    private static final int EXTRA_COUNT = 2;
    private static final int TOTAL_COUNT = PRIMARY_COUNT + EXTRA_COUNT;

    private final RecommendationConditionAggregator conditionAggregator;
    private final RestaurantCandidateSearchService candidateSearchService;
    private final RecommendationPromptBuilder promptBuilder;
    private final OpenAiChatClient openAiChatClient;
    private final ObjectMapper objectMapper;

    @Override
    public RecommendationResult recommend(RecommendationRequest request) {
        AggregatedCondition condition = conditionAggregator.aggregate(request.personalOptions());
        Set<String> excludedCandidateIds = Set.copyOf(request.excludedRestaurantIds());
        List<RestaurantCandidate> candidates = candidateSearchService.search(request.commonOption(), condition, excludedCandidateIds);
        Map<String, RestaurantCandidate> candidateById = candidates.stream()
                .collect(Collectors.toMap(RestaurantCandidate::id, Function.identity()));

        String systemPrompt = promptBuilder.buildSystemPrompt();
        String userPrompt = promptBuilder.buildUserPrompt(request.commonOption(), condition, candidates, request.preferenceNote());
        String rawContent = openAiChatClient.requestStructuredJson(systemPrompt, userPrompt);

        AiSelectionPayload payload = parsePayload(rawContent);
        List<AiSelection> selections = validateSelections(payload);
        List<RestaurantRecommendation> recommendations = selections.stream()
                .map(selection -> toRecommendation(selection, candidateById))
                .toList();

        List<RestaurantRecommendation> primary = filterAndSort(recommendations, Tier.PRIMARY);
        List<RestaurantRecommendation> extra = filterAndSort(recommendations, Tier.EXTRA);

        return new RecommendationResult(condition.participantCount(), primary, extra);
    }

    private AiSelectionPayload parsePayload(String rawContent) {
        try {
            return objectMapper.readValue(rawContent, AiSelectionPayload.class);
        } catch (JsonProcessingException e) {
            throw new AiRecommendationException("AI 추천 응답을 JSON으로 해석하지 못했습니다: " + rawContent, e);
        }
    }

    private List<AiSelection> validateSelections(AiSelectionPayload payload) {
        if (payload == null || payload.selections() == null) {
            throw new AiRecommendationException("AI 추천 응답이 비어 있습니다.");
        }

        List<AiSelection> selections = payload.selections();
        long primaryCount = selections.stream().filter(s -> s.tier() == Tier.PRIMARY).count();
        long extraCount = selections.stream().filter(s -> s.tier() == Tier.EXTRA).count();
        long distinctCandidateCount = selections.stream().map(AiSelection::candidateId).distinct().count();

        if (primaryCount != PRIMARY_COUNT || extraCount != EXTRA_COUNT || distinctCandidateCount != TOTAL_COUNT) {
            throw new AiRecommendationException(
                    "AI 추천 결과 개수/중복이 올바르지 않습니다. (primary=%d, extra=%d, 중복제외개수=%d, 기대값 primary=%d, extra=%d)"
                            .formatted(primaryCount, extraCount, distinctCandidateCount, PRIMARY_COUNT, EXTRA_COUNT));
        }

        return selections;
    }

    private RestaurantRecommendation toRecommendation(AiSelection selection, Map<String, RestaurantCandidate> candidateById) {
        RestaurantCandidate candidate = candidateById.get(selection.candidateId());
        if (candidate == null) {
            throw new AiRecommendationException("AI가 candidates 목록에 없는 음식점을 선택했습니다: " + selection.candidateId());
        }
        return new RestaurantRecommendation(
                candidate.id(),
                selection.rank(),
                selection.tier(),
                candidate.name(),
                candidate.category(),
                candidate.roadAddress(),
                candidate.address(),
                candidate.latitude(),
                candidate.longitude(),
                selection.reason()
        );
    }

    private List<RestaurantRecommendation> filterAndSort(List<RestaurantRecommendation> recommendations, Tier tier) {
        return recommendations.stream()
                .filter(r -> r.tier() == tier)
                .sorted(Comparator.comparingInt(RestaurantRecommendation::rank))
                .toList();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record AiSelectionPayload(List<AiSelection> selections) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record AiSelection(String candidateId, int rank, Tier tier, String reason) {
    }
}
