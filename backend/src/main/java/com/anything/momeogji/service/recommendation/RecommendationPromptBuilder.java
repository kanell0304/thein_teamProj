package com.anything.momeogji.service.recommendation;

import com.anything.momeogji.dto.recommendation.AggregatedCondition;
import com.anything.momeogji.dto.recommendation.CommonOptionRequest;
import com.anything.momeogji.dto.recommendation.RestaurantCandidate;
import com.anything.momeogji.mydata.processing.model.MyDataRestaurantData;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * OpenAI에 보낼 system/user 프롬프트를 만든다.
 * AI는 음식점을 새로 만들어내지 않고 candidates(카카오 실검색 결과) 목록 안에서만 골라야 한다.
 * 참여자 개인의 원본 데이터(누가 무엇을 좋아하는지)는 절대 그대로 보내지 않고,
 * {@link RecommendationConditionAggregator}가 만든 그룹 단위 집계값만 전달한다.
 */
@Component
public class RecommendationPromptBuilder {

    // openAI에 보낼 프롬프트 작성
    // AI가 음식점 실존 여부를 스스로 지어내다 보니 실제와 다른 경우가 많아, 카카오 실검색 후보 중에서만 고르도록 규칙을 바꿈
    private static final String SYSTEM_PROMPT = """
            당신은 '모먹지' 서비스의 모임 음식점 추천 AI입니다. 아래 규칙을 반드시 지키세요.
            1. 반드시 candidates 목록에 있는 음식점 중에서만 골라야 합니다. 목록에 없는 음식점을 새로 만들어내거나 이름·주소를 바꾸지 마세요.
            2. 고른 음식점의 candidates 항목에 있는 id 값을 그대로 candidateId로 반환하세요.
            3. 특정 참여자의 개인 결제 이력이나 취향을 직접 언급하지 말고, 그룹 전체의 공통 조건을 근거로 추천 이유를 설명하세요.
            4. excludedFoods 목록에 해당하는 메뉴를 주력으로 하는 음식점은 후보에서 제외하세요.
            5. candidates는 이미 도보 가능 거리 안의 후보로만 좁혀져 있습니다. 추천 이유를 쓸 때 거리·교통 편의성을 주된 근거로 반복하지 말고, categoryPriority(선호 카테고리 우선순위), minBudget(1인당 예산 상한), parkingRequiredCount, atmospherePriority 등 다른 조건을 우선적인 근거로 삼으세요.
            6. preferenceNote가 주어지면 그 내용을 다른 조건보다 최우선으로 반영하세요.
            7. 반드시 candidates 중 서로 다른 3곳을 골라 rank 1~3을 매기세요. 같은 후보를 중복 선택하지 마세요.
            8. 응답은 지정된 JSON 스키마 형식으로만 작성하고, 다른 설명 텍스트는 포함하지 마세요.
            """;

    private final ObjectMapper objectMapper;

    public RecommendationPromptBuilder(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    // 작성된 프롬프트를 전달
    public String buildSystemPrompt() {
        return SYSTEM_PROMPT;
    }

    public String buildUserPrompt(CommonOptionRequest commonOption, AggregatedCondition condition,
                                   List<RestaurantCandidate> candidates,
                                   List<MyDataRestaurantData> myDataRestaurants,
                                   String preferenceNote) {
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("common", commonOption);
            payload.put("condition", condition);
            payload.put("candidates", candidates);
            payload.put("myDataRestaurants", myDataRestaurants);
            if (preferenceNote != null && !preferenceNote.isBlank()) {
                payload.put("preferenceNote", preferenceNote);
            }
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("추천 요청 프롬프트를 생성하지 못했습니다.", e);
        }
    }
}
