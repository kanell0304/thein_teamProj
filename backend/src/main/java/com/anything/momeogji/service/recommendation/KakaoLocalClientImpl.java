package com.anything.momeogji.service.recommendation;

import com.anything.momeogji.config.KakaoProperties;
import com.anything.momeogji.dto.recommendation.RestaurantCandidate;
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

/**
 * 실제 카카오 로컬 API(GET /v2/local/search/keyword.json) 호출 구현체.
 * 좌표 주변의 실존 음식점을 검색해 AI에게 "고를 수 있는 후보"로 제공하는 용도이며,
 * 검색이 실패해도 예외를 던지지 않고 빈 리스트를 돌려줘 호출 측(후보 풀 구성 로직)이 다른 키워드/반경으로 계속 시도할 수 있게 한다.
 */
@Component
public class KakaoLocalClientImpl implements KakaoLocalClient {

    private static final Logger log = LoggerFactory.getLogger(KakaoLocalClientImpl.class);
    private static final String RESTAURANT_CATEGORY_GROUP_CODE = "FD6";

    private final RestClient restClient;

    public KakaoLocalClientImpl(RestClient.Builder restClientBuilder, KakaoProperties properties) {
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
    public List<RestaurantCandidate> searchNearby(String keyword, double longitude, double latitude, int radiusMeters, int size) {
        if (keyword == null || keyword.isBlank()) {
            return List.of();
        }

        try {
            KakaoKeywordSearchResponse response = restClient.get()
                    .uri(uriBuilder -> uriBuilder.path("/v2/local/search/keyword.json")
                            .queryParam("query", keyword)
                            .queryParam("x", longitude)
                            .queryParam("y", latitude)
                            .queryParam("radius", radiusMeters)
                            .queryParam("category_group_code", RESTAURANT_CATEGORY_GROUP_CODE)
                            .queryParam("sort", "distance")
                            .queryParam("size", size)
                            .build())
                    .retrieve()
                    .body(KakaoKeywordSearchResponse.class);

            if (response == null || response.documents() == null) {
                return List.of();
            }

            return response.documents().stream()
                    .map(KakaoLocalClientImpl::toCandidate)
                    .toList();
        } catch (RestClientException e) {
            log.warn("카카오 키워드 검색 실패, 이 키워드는 이번 후보 풀에서 제외합니다. keyword={}, cause={}", keyword, e.getMessage());
            return List.of();
        }
    }

    private static RestaurantCandidate toCandidate(KakaoKeywordSearchResponse.Document doc) {
        return new RestaurantCandidate(
                doc.id(),
                doc.placeName(),
                extractCategory(doc),
                doc.roadAddressName(),
                doc.addressName(),
                parseDouble(doc.y()),
                parseDouble(doc.x()),
                parseInt(doc.distance())
        );
    }

    /**
     * category_group_name은 FD6 결과에서 항상 "음식점"으로만 뭉뚱그려져 있어 쓸모가 없다.
     * category_name("음식점 > 한식 > 육류,고기")에서 실제 세부 카테고리(두 번째 구간)를 뽑아 쓴다.
     */
    private static String extractCategory(KakaoKeywordSearchResponse.Document doc) {
        String categoryName = doc.categoryName();
        if (categoryName != null && !categoryName.isBlank()) {
            String[] segments = categoryName.split(">");
            if (segments.length >= 2) {
                return segments[1].trim();
            }
            return categoryName.trim();
        }
        return doc.categoryGroupName();
    }

    private static Double parseDouble(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Double.valueOf(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static Integer parseInt(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Integer.valueOf(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record KakaoKeywordSearchResponse(List<Document> documents) {

        @JsonIgnoreProperties(ignoreUnknown = true)
        record Document(
                String id,
                @JsonProperty("place_name") String placeName,
                @JsonProperty("category_name") String categoryName,
                @JsonProperty("category_group_name") String categoryGroupName,
                @JsonProperty("road_address_name") String roadAddressName,
                @JsonProperty("address_name") String addressName,
                String x,
                String y,
                String distance
        ) {
        }
    }
}
