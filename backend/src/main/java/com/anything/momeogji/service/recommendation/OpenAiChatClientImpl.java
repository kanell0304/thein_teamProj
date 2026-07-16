package com.anything.momeogji.service.recommendation;

import com.anything.momeogji.config.recommendation.OpenAiProperties;
import com.anything.momeogji.exception.recommendation.AiRecommendationException;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;


// 실제 OpenAI /chat/completions 엔드포인트를 호출하는 구현체.
// response_format=json_schema(strict)를 사용해 "음식점 3개" 형식을 모델 단에서부터 강제한다.

@Component
public class OpenAiChatClientImpl implements OpenAiChatClient {

    // openAI에게 음식점을 추천받을때 받아야하는 데이터 값을 미리 지정
    // AI가 이름/주소/좌표를 직접 만들어내지 않도록, candidates 목록의 id만 고르게 하는 구조로 최소화함
    private static final String RECOMMENDATION_JSON_SCHEMA = """
            {
              "name": "restaurant_recommendation",
              "strict": true,
              "schema": {
                "type": "object",
                "properties": {
                  "selections": {
                    "type": "array",
                    "minItems": 3,
                    "maxItems": 3,
                    "items": {
                      "type": "object",
                      "properties": {
                        "candidateId": { "type": "string" },
                        "rank": { "type": "integer" },
                        "reason": { "type": "string" }
                      },
                      "required": ["candidateId", "rank", "reason"],
                      "additionalProperties": false
                    }
                  }
                },
                "required": ["selections"],
                "additionalProperties": false
              }
            }
            """;

    private final RestClient restClient;
    private final OpenAiProperties properties;
    private final Map<String, Object> jsonSchema;

    public OpenAiChatClientImpl(RestClient.Builder restClientBuilder, OpenAiProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.restClient = restClientBuilder
                .baseUrl(properties.baseUrl())
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + properties.apiKey())
                .requestFactory(timeoutAwareRequestFactory())
                .build();
        this.jsonSchema = parseSchema(objectMapper);
    }

    // 기본 RestClient는 타임아웃이 없어서 OpenAI가 응답을 안 주면 요청이 무한정 걸린다.
    // 연결/응답 각각에 상한을 둬서 문제가 있으면 빠르게 AiRecommendationException으로 드러나게 한다.
    // (SimpleClientHttpRequestFactory/HttpURLConnection은 청크 응답 처리가 불안정해 JDK HttpClient 기반을 사용한다)
    // gpt-5.5는 reasoning 단계를 거치고 나서 답하기 때문에 구조화 추천 기준 실측으로 수십 초가 걸릴 수 있어 넉넉히 잡는다.
    private static ClientHttpRequestFactory timeoutAwareRequestFactory() {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(Duration.ofSeconds(150));
        return factory;
    }

    private static Map<String, Object> parseSchema(ObjectMapper objectMapper) {
        try {
            return objectMapper.readValue(RECOMMENDATION_JSON_SCHEMA, new TypeReference<>() {
            });
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("추천 응답 JSON 스키마 정의를 읽지 못했습니다.", e);
        }
    }

    @Override
    public String requestStructuredJson(String systemPrompt, String userPrompt) {
        Map<String, Object> requestBody = new LinkedHashMap<>();
        requestBody.put("model", properties.model());
        requestBody.put("messages", List.of(
                Map.of("role", "system", "content", systemPrompt),
                Map.of("role", "user", "content", userPrompt)
        ));
        requestBody.put("response_format", Map.of(
                "type", "json_schema",
                "json_schema", jsonSchema
        ));
        // reasoning_effort가 낮을수록(특히 none) 실측상 5~10배 빨라지고, 주소 품질도 준수해 기본값으로 사용한다.
        if (properties.reasoningEffort() != null && !properties.reasoningEffort().isBlank()) {
            requestBody.put("reasoning_effort", properties.reasoningEffort());
        }

        ChatCompletionResponse response;
        try {
            response = restClient.post()
                    .uri("/chat/completions")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(requestBody)
                    .retrieve()
                    .body(ChatCompletionResponse.class);
        } catch (RestClientException e) {
            throw new AiRecommendationException("OpenAI 호출에 실패했습니다: " + e.getMessage(), e);
        }

        if (response == null || response.choices() == null || response.choices().isEmpty()) {
            throw new AiRecommendationException("OpenAI로부터 추천 응답을 받지 못했습니다.");
        }
        return response.choices().get(0).message().content();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record ChatCompletionResponse(List<Choice> choices) {
        @JsonIgnoreProperties(ignoreUnknown = true)
        record Choice(Message message) {
        }

        @JsonIgnoreProperties(ignoreUnknown = true)
        record Message(String role, String content) {
        }
    }
}
