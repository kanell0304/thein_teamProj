package com.anything.momeogji.service.recommendation;

import com.anything.momeogji.dto.recommendation.AggregatedCondition;
import com.anything.momeogji.dto.recommendation.CommonOptionRequest;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * OpenAI에 보낼 system/user 프롬프트를 만든다.
 * 참여자 개인의 원본 데이터(누가 무엇을 좋아하는지)는 절대 그대로 보내지 않고,
 * {@link RecommendationConditionAggregator}가 만든 그룹 단위 집계값만 전달한다.
 */
@Component
public class RecommendationPromptBuilder {

    // openAI에 보낼 프롬프트 작성
    // 일단 1차 회의때 정한 옵션들을 기준으로 작성했음
    private static final String SYSTEM_PROMPT = """
            당신은 '모먹지' 서비스의 모임 음식점 추천 AI입니다. 아래 규칙을 반드시 지키세요.
            1. 실제로 존재하는 음식점만 추천하세요. 상호명, 주소, 좌표를 확신하거나 확보 할 수 없으면 지어내지 말고 해당 필드를 null로 두세요.
            2. 특정 참여자의 개인 결제 이력이나 취향을 직접 언급하지 말고, 그룹 전체의 공통 조건을 근거로 추천 이유를 설명하세요.
            3. excludedFoods 목록에 해당하는 메뉴를 주력으로 하는 음식점은 후보에서 제외하세요.
            4. destination 좌표 기준 averageWalkMinutes, categoryPriority(선호 카테고리 우선순위), minBudget(1인당 예산 상한), parkingRequiredCount, atmospherePriority를 종합적으로 고려하세요.
            5. 반드시 총 5곳을 추천하되 rank 1~3은 tier=PRIMARY, rank 4~5는 tier=EXTRA로 지정하세요. PRIMARY는 최우선 후보, EXTRA는 참여자들이 전부 재추천을 원할 때 바로 제시할 예비 후보입니다.
            6. 응답은 지정된 JSON 스키마 형식으로만 작성하고, 다른 설명 텍스트는 포함하지 마세요.
            """;

    private final ObjectMapper objectMapper;

    public RecommendationPromptBuilder(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    // 작성된 프롬프트를 전달
    public String buildSystemPrompt() {
        return SYSTEM_PROMPT;
    }

    public String buildUserPrompt(CommonOptionRequest commonOption, AggregatedCondition condition) {
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("common", commonOption);
            payload.put("condition", condition);
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("추천 요청 프롬프트를 생성하지 못했습니다.", e);
        }
    }
}
