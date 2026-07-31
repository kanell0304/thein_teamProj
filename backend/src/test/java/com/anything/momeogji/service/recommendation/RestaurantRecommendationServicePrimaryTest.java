package com.anything.momeogji.service.recommendation;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * 실제 앱 전체를 띄우지 않고, RestaurantRecommendationService 구현체 2개만 스프링 컨테이너에
 * 등록해서 @Primary가 실제로 기본 주입을 결정하는지 확인한다.
 */
class RestaurantRecommendationServicePrimaryTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withBean(RecommendationConditionAggregator.class)
            .withBean(RestaurantCandidateSearchService.class, () -> mock(RestaurantCandidateSearchService.class))
            .withBean(RecommendationPromptBuilder.class, () -> new RecommendationPromptBuilder(new ObjectMapper()))
            .withBean(OpenAiChatClient.class, () -> mock(OpenAiChatClient.class))
            .withBean(GooglePlacesImageClient.class, () -> mock(GooglePlacesImageClient.class))
            .withBean(KakaoImageSearchClient.class, () -> mock(KakaoImageSearchClient.class))
            .withBean(ObjectMapper.class, ObjectMapper::new)
            .withUserConfiguration(RestaurantRecommendationServiceImpl.class, MockRestaurantRecommendationServiceImpl.class);

    @Test
    void 두_구현체_모두_빈으로_등록된다() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(RestaurantRecommendationServiceImpl.class);
            assertThat(context).hasSingleBean(MockRestaurantRecommendationServiceImpl.class);
        });
    }

    @Test
    void 지정_없이_주입받으면_Primary가_붙은_실제_구현체가_선택된다() {
        contextRunner.run(context -> {
            RestaurantRecommendationService injected = context.getBean(RestaurantRecommendationService.class);
            assertThat(injected).isInstanceOf(RestaurantRecommendationServiceImpl.class);
        });
    }

    @Test
    void Qualifier에_해당하는_빈_이름으로_찾으면_Mock_구현체를_받을_수_있다() {
        contextRunner.run(context -> {
            Object mockBean = context.getBean("mockRestaurantRecommendationServiceImpl");
            assertThat(mockBean).isInstanceOf(MockRestaurantRecommendationServiceImpl.class);
        });
    }
}
