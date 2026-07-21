package com.anything.momeogji.service.recommendation;

import com.anything.momeogji.dto.recommendation.CommonOptionRequest;
import com.anything.momeogji.dto.recommendation.PersonalOptionRequest;
import com.anything.momeogji.dto.recommendation.RecommendationRequest;
import com.anything.momeogji.dto.recommendation.RecommendationResult;
import com.anything.momeogji.dto.recommendation.RestaurantCandidate;
import com.anything.momeogji.exception.recommendation.AiRecommendationException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/** 실제 네트워크 호출 없이, 카카오 후보 검색 + OpenAI 응답 + 이미지 검색(구글 우선/카카오 폴백)을 서비스가 올바르게 조합/검증하는지 확인한다. */
@ExtendWith(MockitoExtension.class)
class RestaurantRecommendationServiceImplTest {

    // rank 1~3 순서대로 candidateId c1~c3를 선택한 정상 AI 응답
    private static final String VALID_RESPONSE_JSON = """
            {
              "selections": [
                {"candidateId":"c1","rank":1,"reason":"그룹 예산과 접근성에 부합"},
                {"candidateId":"c2","rank":2,"reason":"선호 카테고리 상위"},
                {"candidateId":"c3","rank":3,"reason":"주차 가능"}
              ]
            }
            """;

    private static final List<RestaurantCandidate> CANDIDATES = List.of(
            new RestaurantCandidate("c1", "모먹지 김밥천국", "한식", "서울 강남구 테헤란로 1", "서울 강남구 역삼동 1", 37.499, 127.028, 100),
            new RestaurantCandidate("c2", "스시모먹지", "일식", "서울 강남구 테헤란로 2", "서울 강남구 역삼동 2", 37.500, 127.029, 150),
            new RestaurantCandidate("c3", "모먹지 삼겹살", "한식", "서울 강남구 테헤란로 3", "서울 강남구 역삼동 3", 37.501, 127.030, 200)
    );

    @Mock
    private RestaurantCandidateSearchService candidateSearchService;

    @Mock
    private OpenAiChatClient openAiChatClient;

    @Mock
    private GooglePlacesImageClient googlePlacesImageClient;

    @Mock
    private KakaoImageSearchClient kakaoImageSearchClient;

    private RestaurantRecommendationServiceImpl service;

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        service = new RestaurantRecommendationServiceImpl(
                new RecommendationConditionAggregator(),
                candidateSearchService,
                new RecommendationPromptBuilder(objectMapper),
                openAiChatClient,
                googlePlacesImageClient,
                kakaoImageSearchClient,
                objectMapper
        );
        given(candidateSearchService.search(any(), any(), any())).willReturn(CANDIDATES);
        // 검증 실패로 일찍 끝나는 테스트에서는 이미지 조회까지 안 가므로 lenient 처리.
        lenient().when(googlePlacesImageClient.searchFirstImageUrl(any(), anyDouble(), anyDouble())).thenReturn(Optional.empty());
        lenient().when(kakaoImageSearchClient.searchFirstImageUrl(any())).thenReturn(Optional.empty());
    }

    @Test
    void 정상_응답이면_추천_3곳을_실제_후보_데이터로_채운다() {
        given(openAiChatClient.requestStructuredJson(any(), any())).willReturn(VALID_RESPONSE_JSON);

        RecommendationResult result = service.recommend(sampleRequest(List.of(), null));

        assertThat(result.participantCount()).isEqualTo(2);
        assertThat(result.recommendations()).hasSize(3);
        // AI는 candidateId만 골랐을 뿐이고, 실제 이름/주소/좌표는 후보 데이터에서 그대로 채워져야 한다.
        assertThat(result.recommendations().get(0).id()).isEqualTo("c1");
        assertThat(result.recommendations().get(0).name()).isEqualTo("모먹지 김밥천국");
        assertThat(result.recommendations().get(0).roadAddress()).isEqualTo("서울 강남구 테헤란로 1");
        assertThat(result.recommendations().get(2).name()).isEqualTo("모먹지 삼겹살");
    }

    @Test
    void 구글_이미지_검색_결과가_있으면_카카오보다_우선한다() {
        given(openAiChatClient.requestStructuredJson(any(), any())).willReturn(VALID_RESPONSE_JSON);
        given(googlePlacesImageClient.searchFirstImageUrl(eq("모먹지 김밥천국"), eq(37.499), eq(127.028)))
                .willReturn(Optional.of("https://google.example.com/kimbap.jpg"));

        RecommendationResult result = service.recommend(sampleRequest(List.of(), null));

        assertThat(result.recommendations().get(0).imageUrl()).isEqualTo("https://google.example.com/kimbap.jpg");
        verify(kakaoImageSearchClient, never()).searchFirstImageUrl("모먹지 김밥천국");
    }

    @Test
    void 구글이_결과가_없으면_카카오_이미지_검색으로_폴백한다() {
        given(openAiChatClient.requestStructuredJson(any(), any())).willReturn(VALID_RESPONSE_JSON);
        given(kakaoImageSearchClient.searchFirstImageUrl("모먹지 김밥천국"))
                .willReturn(Optional.of("https://kakao.example.com/kimbap.jpg"));

        RecommendationResult result = service.recommend(sampleRequest(List.of(), null));

        assertThat(result.recommendations().get(0).imageUrl()).isEqualTo("https://kakao.example.com/kimbap.jpg");
        // 구글도 카카오도 결과가 없는 나머지는 null로 채워져야 한다(이미지 실패가 추천 자체를 막지 않음).
        assertThat(result.recommendations().get(1).imageUrl()).isNull();
    }

    @Test
    void 재추천_요청의_excludedRestaurantIds가_후보_검색에_그대로_전달된다() {
        given(openAiChatClient.requestStructuredJson(any(), any())).willReturn(VALID_RESPONSE_JSON);

        service.recommend(sampleRequest(List.of("old1", "old2"), null));

        verify(candidateSearchService).search(any(), any(), eq(Set.of("old1", "old2")));
    }

    @Test
    void preferenceNote가_있으면_AI에게_보내는_user_prompt에_포함된다() {
        given(openAiChatClient.requestStructuredJson(any(), any())).willReturn(VALID_RESPONSE_JSON);

        service.recommend(sampleRequest(List.of(), "가성비 위주로 골라줘"));

        ArgumentCaptor<String> userPromptCaptor = ArgumentCaptor.forClass(String.class);
        verify(openAiChatClient).requestStructuredJson(any(), userPromptCaptor.capture());
        assertThat(userPromptCaptor.getValue()).contains("가성비 위주로 골라줘");
    }

    @Test
    void AI_응답_개수가_3이_아니면_예외를_던진다() {
        given(openAiChatClient.requestStructuredJson(any(), any())).willReturn("""
                { "selections": [
                  {"candidateId":"c1","rank":1,"reason":"r"}
                ] }
                """);

        assertThatThrownBy(() -> service.recommend(sampleRequest(List.of(), null)))
                .isInstanceOf(AiRecommendationException.class);
    }

    @Test
    void AI가_같은_후보를_중복_선택하면_예외를_던진다() {
        given(openAiChatClient.requestStructuredJson(any(), any())).willReturn("""
                { "selections": [
                  {"candidateId":"c1","rank":1,"reason":"r"},
                  {"candidateId":"c1","rank":2,"reason":"r"},
                  {"candidateId":"c3","rank":3,"reason":"r"}
                ] }
                """);

        assertThatThrownBy(() -> service.recommend(sampleRequest(List.of(), null)))
                .isInstanceOf(AiRecommendationException.class);
    }

    @Test
    void AI가_후보_목록에_없는_candidateId를_고르면_예외를_던진다() {
        given(openAiChatClient.requestStructuredJson(any(), any())).willReturn("""
                { "selections": [
                  {"candidateId":"c1","rank":1,"reason":"r"},
                  {"candidateId":"c2","rank":2,"reason":"r"},
                  {"candidateId":"존재하지않는id","rank":3,"reason":"r"}
                ] }
                """);

        assertThatThrownBy(() -> service.recommend(sampleRequest(List.of(), null)))
                .isInstanceOf(AiRecommendationException.class);
    }

    @Test
    void AI_응답이_JSON이_아니면_예외를_던진다() {
        given(openAiChatClient.requestStructuredJson(any(), any())).willReturn("이건 JSON이 아닙니다");

        assertThatThrownBy(() -> service.recommend(sampleRequest(List.of(), null)))
                .isInstanceOf(AiRecommendationException.class);
    }

    private RecommendationRequest sampleRequest(List<String> excludedRestaurantIds, String preferenceNote) {
        CommonOptionRequest common = new CommonOptionRequest(
                "강남역", 37.498, 127.027, LocalDateTime.of(2026, 7, 20, 12, 0), "식사"
        );
        List<PersonalOptionRequest> personal = List.of(
                new PersonalOptionRequest(1L, 5, List.of("한식"), 15000, false, List.of(), "룸"),
                new PersonalOptionRequest(2L, 10, List.of("한식"), 20000, true, List.of("고수"), "개방형")
        );
        return new RecommendationRequest(common, personal, excludedRestaurantIds, preferenceNote);
    }
}
