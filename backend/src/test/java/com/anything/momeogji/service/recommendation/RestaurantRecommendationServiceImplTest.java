package com.anything.momeogji.service.recommendation;

import com.anything.momeogji.dto.recommendation.CommonOptionRequest;
import com.anything.momeogji.dto.recommendation.PersonalOptionRequest;
import com.anything.momeogji.dto.recommendation.RecommendationRequest;
import com.anything.momeogji.dto.recommendation.RecommendationResult;
import com.anything.momeogji.exception.recommendation.AiRecommendationException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

/** 실제 네트워크 호출 없이, OpenAiChatClient가 반환한 JSON을 서비스가 올바르게 가공/검증하는지 확인한다. */
@ExtendWith(MockitoExtension.class)
class RestaurantRecommendationServiceImplTest {

    private static final String VALID_RESPONSE_JSON = """
            {
              "restaurants": [
                {"rank":1,"tier":"PRIMARY","name":"모먹지 김밥천국","category":"한식","roadAddress":"서울 강남구 테헤란로 1","address":"서울 강남구 역삼동 1","latitude":37.499,"longitude":127.028,"reason":"그룹 예산과 접근성에 부합"},
                {"rank":2,"tier":"PRIMARY","name":"스시모먹지","category":"일식","roadAddress":"서울 강남구 테헤란로 2","address":"서울 강남구 역삼동 2","latitude":37.500,"longitude":127.029,"reason":"선호 카테고리 상위"},
                {"rank":3,"tier":"PRIMARY","name":"모먹지 삼겹살","category":"한식","roadAddress":"서울 강남구 테헤란로 3","address":"서울 강남구 역삼동 3","latitude":37.501,"longitude":127.030,"reason":"주차 가능"},
                {"rank":4,"tier":"EXTRA","name":"모먹지 파스타","category":"양식","roadAddress":"서울 강남구 테헤란로 4","address":"서울 강남구 역삼동 4","latitude":37.502,"longitude":127.031,"reason":"예비 후보"},
                {"rank":5,"tier":"EXTRA","name":"모먹지 훠궈","category":"중식","roadAddress":"서울 강남구 테헤란로 5","address":"서울 강남구 역삼동 5","latitude":37.503,"longitude":127.032,"reason":"예비 후보"}
              ]
            }
            """;

    @Mock
    private OpenAiChatClient openAiChatClient;

    private RestaurantRecommendationServiceImpl service;

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        service = new RestaurantRecommendationServiceImpl(
                new RecommendationConditionAggregator(),
                new RecommendationPromptBuilder(objectMapper),
                openAiChatClient,
                objectMapper
        );
    }

    @Test
    void 정상_응답이면_주추천_3개와_예비추천_2개로_분리한다() {
        given(openAiChatClient.requestStructuredJson(any(), any())).willReturn(VALID_RESPONSE_JSON);

        RecommendationResult result = service.recommend(sampleRequest());

        assertThat(result.participantCount()).isEqualTo(2);
        assertThat(result.primaryRecommendations()).hasSize(3);
        assertThat(result.extraRecommendations()).hasSize(2);
        assertThat(result.primaryRecommendations().get(0).name()).isEqualTo("모먹지 김밥천국");
        assertThat(result.extraRecommendations().get(1).name()).isEqualTo("모먹지 훠궈");
    }

    @Test
    void AI_응답_개수가_3plus2가_아니면_예외를_던진다() {
        given(openAiChatClient.requestStructuredJson(any(), any())).willReturn("""
                { "restaurants": [
                  {"rank":1,"tier":"PRIMARY","name":"a","category":"한식","roadAddress":null,"address":null,"latitude":null,"longitude":null,"reason":"r"}
                ] }
                """);

        assertThatThrownBy(() -> service.recommend(sampleRequest()))
                .isInstanceOf(AiRecommendationException.class);
    }

    @Test
    void AI_응답이_JSON이_아니면_예외를_던진다() {
        given(openAiChatClient.requestStructuredJson(any(), any())).willReturn("이건 JSON이 아닙니다");

        assertThatThrownBy(() -> service.recommend(sampleRequest()))
                .isInstanceOf(AiRecommendationException.class);
    }

    private RecommendationRequest sampleRequest() {
        CommonOptionRequest common = new CommonOptionRequest(
                "강남역", 37.498, 127.027, LocalDateTime.of(2026, 7, 20, 12, 0), "식사"
        );
        List<PersonalOptionRequest> personal = List.of(
                new PersonalOptionRequest("u1", 5, List.of("한식"), 15000, false, List.of(), "룸"),
                new PersonalOptionRequest("u2", 10, List.of("한식"), 20000, true, List.of("고수"), "개방형")
        );
        return new RecommendationRequest(common, personal);
    }
}
