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

    private static final int RECOMMENDATION_COUNT = 3;

    private final RecommendationConditionAggregator conditionAggregator;
    private final RestaurantCandidateSearchService candidateSearchService;
    private final RecommendationPromptBuilder promptBuilder;
    private final OpenAiChatClient openAiChatClient;
    private final GooglePlacesImageClient googlePlacesImageClient;
    private final KakaoImageSearchClient kakaoImageSearchClient;
    private final ObjectMapper objectMapper;

    // ai 추천 로직
    @Override
    public RecommendationResult recommend(RecommendationRequest request) {
        AggregatedCondition condition = conditionAggregator.aggregate(request.personalOptions()); // 개인 선택 분위기
        Set<String> excludedCandidateIds = Set.copyOf(request.excludedRestaurantIds()); // 제외하고 싶은 음식
        List<RestaurantCandidate> candidates = candidateSearchService.search(request.commonOption(), condition, excludedCandidateIds); // 검색/추천
        Map<String, RestaurantCandidate> candidateById = candidates.stream() // 추천 목록을 리스트로 저장
                .collect(Collectors.toMap(RestaurantCandidate::id, Function.identity()));

        String systemPrompt = promptBuilder.buildSystemPrompt(); // ai 프롬프트 생성
        String userPrompt = promptBuilder.buildUserPrompt(request.commonOption(), condition, candidates, request.preferenceNote()); // 각각의 개인 선택 사항을 프롬프트에 추가
        String rawContent = openAiChatClient.requestStructuredJson(systemPrompt, userPrompt); // 위의 2개를 포함하여 api 전송 및 결과(응답) 저장

        AiSelectionPayload payload = parsePayload(rawContent); // 응답 받은 데이터를 파싱
        List<AiSelection> selections = validateSelections(payload); // 추천 리스트가 비어있는지 확인
        List<RestaurantRecommendation> recommendations = selections.stream() // 추천받은 음식점들을 리스트로 저장 ->
                .map(selection -> toRecommendation(selection, candidateById))
                .sorted(Comparator.comparingInt(RestaurantRecommendation::rank))
                .toList();

        return new RecommendationResult(condition.participantCount(), attachImages(recommendations));
    }

    private AiSelectionPayload parsePayload(String rawContent) {
        try {
            return objectMapper.readValue(rawContent, AiSelectionPayload.class);
        } catch (JsonProcessingException e) {
            throw new AiRecommendationException("AI 추천 응답을 JSON으로 해석하지 못했습니다: " + rawContent, e);
        }
    }

    // 추천 응답이 비어있는지 확인하는 메서드
    private List<AiSelection> validateSelections(AiSelectionPayload payload) {
        if (payload == null || payload.selections() == null) { // 응답 값이 null인지 확인
            throw new AiRecommendationException("AI 추천 응답이 비어 있습니다."); // 없으면 예외 발생
        }

        List<AiSelection> selections = payload.selections(); // 있으면 리스트형태로 받아옴
        long distinctCandidateCount = selections.stream().map(AiSelection::candidateId).distinct().count(); // 받아온 리스트 개수 확인

        if (selections.size() != RECOMMENDATION_COUNT || distinctCandidateCount != RECOMMENDATION_COUNT) { // 받아온 리스트 개수가 요구 사항에 맞는 개수 미만일 시
            throw new AiRecommendationException(
                    "AI 추천 결과 개수/중복이 올바르지 않습니다. (개수=%d, 중복제외개수=%d, 기대값=%d)" // 추천 개수가 부족하다는 메세지 예외 발생
                            .formatted(selections.size(), distinctCandidateCount, RECOMMENDATION_COUNT));
        }

        return selections;
    }

    // 음식점 추천 로직
    private RestaurantRecommendation toRecommendation(AiSelection selection, Map<String, RestaurantCandidate> candidateById) {
        RestaurantCandidate candidate = candidateById.get(selection.candidateId());
        if (candidate == null) {
            throw new AiRecommendationException("AI가 candidates 목록에 없는 음식점을 선택했습니다: " + selection.candidateId());
        }
        return new RestaurantRecommendation(
                candidate.id(),
                selection.rank(), // (우선순위인데 현재 사용하지 않음 ㅇㅅㅇ)
                candidate.name(), // 음식점 이름
                candidate.category(), // 카테고리
                candidate.roadAddress(), // 도로명
                candidate.address(), // 주소
                candidate.latitude(), // 위도
                candidate.longitude(), // 경도
                selection.reason(), // 추천 이유
                null
        );
    }

    // AI가 최종적으로 고른 3곳에 대해서만 이미지를 조회.
    // 3건을 순차 호출하면 지연이 그대로 합산되니 병렬로 조회.
    private List<RestaurantRecommendation> attachImages(List<RestaurantRecommendation> recommendations) {
        List<CompletableFuture<RestaurantRecommendation>> futures = recommendations.stream()
                .map(recommendation -> CompletableFuture.supplyAsync(() -> withImage(recommendation)))
                .toList();
        return futures.stream().map(CompletableFuture::join).toList();
    }

    // 구글이 더 정확하므로 먼저 시도하고(그 가게에 실제 등록된 사진), 키가 없거나 검색에 실패하면 카카오 이미지 검색으로 폴백한다.
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
                imageUrl // 이미지 url이 추가됨
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
