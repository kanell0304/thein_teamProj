package com.anything.momeogji.service.recommendation;

import com.anything.momeogji.dto.recommendation.AggregatedCondition;
import com.anything.momeogji.dto.recommendation.RecommendationRequest;
import com.anything.momeogji.dto.recommendation.RecommendationResult;
import com.anything.momeogji.dto.recommendation.RestaurantCandidate;
import com.anything.momeogji.dto.recommendation.RestaurantRecommendation;
import com.anything.momeogji.exception.recommendation.AiRecommendationException;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.stream.Collectors;

 // OpenAI 추천을 호출해 음식점 3곳을 가공하는 핵심 서비스.
 // 카카오에서 검색한 실제 음식점 후보(candidates) 중에서만 AI가 고르게 하고, 이름/주소/좌표는 항상
 // 그 실검색 데이터에서 채워 넣는다(AI가 직접 만든 값은 신뢰하지 않음).
 // DB 테이블이 아직 확정되지 않았으므로 결과는 영속화하지 않고 요청-응답으로만 다룬다.

@Service
@RequiredArgsConstructor
public class RestaurantRecommendationServiceImpl implements RestaurantRecommendationService {

    private static final Logger log = LoggerFactory.getLogger(RestaurantRecommendationServiceImpl.class);
    private static final int RECOMMENDATION_COUNT = 3;

    private final RecommendationConditionAggregator conditionAggregator;
    private final RestaurantCandidateSearchService candidateSearchService;
    private final RecommendationPromptBuilder promptBuilder;
    private final OpenAiChatClient openAiChatClient;
    private final GooglePlacesImageClient googlePlacesImageClient;
    private final KakaoImageSearchClient kakaoImageSearchClient;
    private final ObjectMapper objectMapper;

    @Override
    public RecommendationResult recommend(RecommendationRequest request) {
        AggregatedCondition condition = conditionAggregator.aggregate(request.personalOptions(), request.myDataRestaurants());
        // MyData 방문 이력이 categoryPriority에 실제로 표를 더했는지 눈으로 확인할 수 있도록 남긴다.
        log.info("추천 조건 집계 결과: categoryPriority={}, myDataRestaurants={}",
                condition.categoryPriority(), request.myDataRestaurants());
        Set<String> excludedCandidateIds = Set.copyOf(request.excludedRestaurantIds());
        List<RestaurantCandidate> candidates = candidateSearchService.search(request.commonOption(), condition, excludedCandidateIds);
        Map<String, RestaurantCandidate> candidateById = candidates.stream()
                .collect(Collectors.toMap(RestaurantCandidate::id, Function.identity()));

        String systemPrompt = promptBuilder.buildSystemPrompt();
        String userPrompt = promptBuilder.buildUserPrompt(
                request.commonOption(),
                condition,
                candidates,
                request.myDataRestaurants(),
                request.preferenceNote()
        );
        String rawContent = openAiChatClient.requestStructuredJson(systemPrompt, userPrompt);

        AiSelectionPayload payload = parsePayload(rawContent);
        List<AiSelection> selections = validateSelections(payload);
        List<RestaurantRecommendation> recommendations = selections.stream()
                .map(selection -> toRecommendation(selection, candidateById))
                .sorted(Comparator.comparingInt(RestaurantRecommendation::rank))
                .toList();

        // AI가 각 후보를 왜 골랐는지 서버 콘솔에서 바로 확인할 수 있도록 남긴다.
        recommendations.forEach(recommendation -> log.info(
                "AI 추천 이유: rank={}, name={}, reason={}",
                recommendation.rank(), recommendation.name(), recommendation.reason()));

        return new RecommendationResult(condition.participantCount(), attachImages(recommendations));
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
        long distinctCandidateCount = selections.stream().map(AiSelection::candidateId).distinct().count();

        if (selections.size() != RECOMMENDATION_COUNT || distinctCandidateCount != RECOMMENDATION_COUNT) {
            throw new AiRecommendationException(
                    "AI 추천 결과 개수/중복이 올바르지 않습니다. (개수=%d, 중복제외개수=%d, 기대값=%d)"
                            .formatted(selections.size(), distinctCandidateCount, RECOMMENDATION_COUNT));
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
                candidate.name(),
                candidate.category(),
                candidate.roadAddress(),
                candidate.address(),
                candidate.latitude(),
                candidate.longitude(),
                selection.reason(),
                null
        );
    }

    // AI가 최종적으로 고른 3곳에 대해서만 이미지를 조회한다(후보 전체에 조회하면 쿼터가 금방 소진됨).
    // 3건을 순차 호출하면 지연이 그대로 합산되니 병렬로 조회한다.
    private List<RestaurantRecommendation> attachImages(List<RestaurantRecommendation> recommendations) {
        List<CompletableFuture<RestaurantRecommendation>> futures = recommendations.stream()
                .map(recommendation -> CompletableFuture.supplyAsync(() -> withImage(recommendation)))
                .toList();
        return futures.stream().map(CompletableFuture::join).toList();
    }

    // 구글이 더 정확하므로 먼저 시도하고(그 가게에 실제 등록된 사진), 키가 없거나 실패하면 카카오 이미지 검색으로 폴백한다.
    private RestaurantRecommendation withImage(RestaurantRecommendation recommendation) {
        Optional<String> googleImage = hasCoordinates(recommendation)
                ? googlePlacesImageClient.searchFirstImageUrl(recommendation.name(), recommendation.latitude(), recommendation.longitude())
                : Optional.empty();

        String imageUrl = googleImage
                .or(() -> kakaoImageSearchClient.searchFirstImageUrl(recommendation.name()))
                .orElse(null);

        return new RestaurantRecommendation(
                recommendation.id(),
                recommendation.rank(),
                recommendation.name(),
                recommendation.category(),
                recommendation.roadAddress(),
                recommendation.address(),
                recommendation.latitude(),
                recommendation.longitude(),
                recommendation.reason(),
                imageUrl
        );
    }

    private boolean hasCoordinates(RestaurantRecommendation recommendation) {
        return recommendation.latitude() != null && recommendation.longitude() != null;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record AiSelectionPayload(List<AiSelection> selections) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record AiSelection(String candidateId, int rank, String reason) {
    }
}
