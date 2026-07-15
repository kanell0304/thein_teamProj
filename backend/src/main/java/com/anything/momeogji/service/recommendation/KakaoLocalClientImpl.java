package com.anything.momeogji.service.recommendation;

import com.anything.momeogji.config.recommendation.KakaoProperties;
import com.anything.momeogji.dto.recommendation.GeocodedAddress;
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
 * 실제 카카오 로컬 API(GET /v2/local/search/address.json) 호출 구현체.
 * 이 호출은 최종 공지에 쓸 음식점 1곳의 주소만 보정하는 부가 기능이므로,
 * 실패해도 예외를 던지지 않고 빈 값을 돌려줘서 호출 측이 AI 원본 좌표로 자연스럽게 폴백하게 한다.
 */
@Component
public class KakaoLocalClientImpl implements KakaoLocalClient {

    private static final Logger log = LoggerFactory.getLogger(KakaoLocalClientImpl.class);

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
    public Optional<GeocodedAddress> searchAddress(String query) {
        if (query == null || query.isBlank()) {
            return Optional.empty();
        }

        try {
            KakaoAddressSearchResponse response = restClient.get()
                    .uri(uriBuilder -> uriBuilder.path("/v2/local/search/address.json")
                            .queryParam("query", query)
                            .build())
                    .retrieve()
                    .body(KakaoAddressSearchResponse.class);

            if (response == null || response.documents() == null || response.documents().isEmpty()) {
                log.info("카카오 주소 검색 결과 없음: {}", query);
                return Optional.empty();
            }

            KakaoAddressSearchResponse.Document doc = response.documents().get(0);
            String roadAddress = doc.roadAddress() != null ? doc.roadAddress().addressName() : null;
            String address = doc.address() != null ? doc.address().addressName() : doc.addressName();

            return Optional.of(new GeocodedAddress(
                    roadAddress,
                    address,
                    parseCoordinate(doc.y()),
                    parseCoordinate(doc.x())
            ));
        } catch (RestClientException e) {
            log.warn("카카오 주소 검색 실패, AI가 준 좌표를 그대로 사용합니다. query={}, cause={}", query, e.getMessage());
            return Optional.empty();
        }
    }

    private static Double parseCoordinate(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Double.valueOf(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record KakaoAddressSearchResponse(List<Document> documents) {

        @JsonIgnoreProperties(ignoreUnknown = true)
        record Document(
                @JsonProperty("address_name") String addressName,
                String x,
                String y,
                @JsonProperty("road_address") RoadAddress roadAddress,
                Address address
        ) {
        }

        @JsonIgnoreProperties(ignoreUnknown = true)
        record RoadAddress(@JsonProperty("address_name") String addressName) {
        }

        @JsonIgnoreProperties(ignoreUnknown = true)
        record Address(@JsonProperty("address_name") String addressName) {
        }
    }
}
