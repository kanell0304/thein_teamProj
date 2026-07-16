package com.anything.momeogji.service.recommendation;

import com.anything.momeogji.config.KakaoProperties;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;
import java.util.Optional;

/**
 * 실제 카카오 이미지 검색 API(GET /v2/search/image) 호출 구현체.
 * "이 가게의 공식 사진"이라는 보장이 없는 참고용 이미지라, 실패하거나 결과가 없어도 추천 자체를 막지 않고
 * 예외 없이 빈 값을 돌려준다.
 */
@Component
public class KakaoImageSearchClientImpl implements KakaoImageSearchClient {

    private static final Logger log = LoggerFactory.getLogger(KakaoImageSearchClientImpl.class);

    private final RestClient restClient;

    public KakaoImageSearchClientImpl(RestClient.Builder restClientBuilder, KakaoProperties properties) {
        this.restClient = restClientBuilder
                .baseUrl(properties.baseUrl())
                .defaultHeader(HttpHeaders.AUTHORIZATION, "KakaoAK " + properties.restApiKey())
                .requestFactory(timeoutAwareRequestFactory())
                .build();
    }

    private static ClientHttpRequestFactory timeoutAwareRequestFactory() {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(Duration.ofSeconds(5));
        return factory;
    }

    @Override
    public Optional<String> searchFirstImageUrl(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return Optional.empty();
        }

        try {
            KakaoImageSearchResponse response = restClient.get()
                    .uri(uriBuilder -> uriBuilder.path("/v2/search/image")
                            .queryParam("query", keyword)
                            .queryParam("size", 1)
                            .build())
                    .retrieve()
                    .body(KakaoImageSearchResponse.class);

            if (response == null || response.documents() == null || response.documents().isEmpty()) {
                return Optional.empty();
            }
            return Optional.ofNullable(response.documents().get(0).thumbnailUrl());
        } catch (RestClientException e) {
            log.warn("카카오 이미지 검색 실패, 이미지 없이 진행합니다. keyword={}, cause={}", keyword, e.getMessage());
            return Optional.empty();
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record KakaoImageSearchResponse(List<Document> documents) {

        @JsonIgnoreProperties(ignoreUnknown = true)
        record Document(
                @JsonProperty("thumbnail_url") String thumbnailUrl,
                @JsonProperty("image_url") String imageUrl
        ) {
        }
    }
}
