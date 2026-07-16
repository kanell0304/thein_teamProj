package com.anything.momeogji.service.recommendation;

import com.anything.momeogji.config.recommendation.GoogleProperties;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 실제 구글 Places API(New) 호출 구현체.
 * 1) Text Search(New)로 이름+좌표(locationBias)에 맞는 Place를 찾아 사진 참조를 얻고
 * 2) Photo(New)로 그 참조를 실제 이미지 URL로 바꾼다.
 * API 키가 비어있거나 호출이 실패해도 예외를 던지지 않고 빈 값을 돌려줘서, 호출 측이 카카오 이미지 검색으로 폴백할 수 있게 한다.
 */
@Component
public class GooglePlacesImageClientImpl implements GooglePlacesImageClient {

    private static final Logger log = LoggerFactory.getLogger(GooglePlacesImageClientImpl.class);
    private static final double LOCATION_BIAS_RADIUS_METERS = 200.0;
    private static final int MAX_PHOTO_WIDTH_PX = 400;

    private final RestClient restClient;
    private final GoogleProperties properties;

    public GooglePlacesImageClientImpl(RestClient.Builder restClientBuilder, GoogleProperties properties) {
        this.properties = properties;
        this.restClient = restClientBuilder
                .baseUrl("https://places.googleapis.com")
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
    public Optional<String> searchFirstImageUrl(String name, double latitude, double longitude) {
        if (name == null || name.isBlank() || properties.placesApiKey() == null || properties.placesApiKey().isBlank()) {
            return Optional.empty();
        }

        try {
            String photoName = findFirstPhotoName(name, latitude, longitude);
            if (photoName == null) {
                return Optional.empty();
            }
            return fetchPhotoUri(photoName);
        } catch (RestClientException e) {
            log.warn("구글 플레이스 이미지 조회 실패, 카카오 이미지 검색으로 폴백합니다. name={}, cause={}", name, e.getMessage());
            return Optional.empty();
        }
    }

    private String findFirstPhotoName(String name, double latitude, double longitude) {
        Map<String, Object> requestBody = Map.of(
                "textQuery", name,
                "locationBias", Map.of(
                        "circle", Map.of(
                                "center", Map.of("latitude", latitude, "longitude", longitude),
                                "radius", LOCATION_BIAS_RADIUS_METERS
                        )
                )
        );

        TextSearchResponse response = restClient.post()
                .uri("/v1/places:searchText")
                .header("X-Goog-Api-Key", properties.placesApiKey())
                .header("X-Goog-FieldMask", "places.photos")
                .contentType(MediaType.APPLICATION_JSON)
                .body(requestBody)
                .retrieve()
                .body(TextSearchResponse.class);

        if (response == null || response.places() == null || response.places().isEmpty()) {
            return null;
        }

        return response.places().stream()
                .map(TextSearchResponse.Place::photos)
                .filter(photos -> photos != null && !photos.isEmpty())
                .map(photos -> photos.get(0).name())
                .findFirst()
                .orElse(null);
    }

    private Optional<String> fetchPhotoUri(String photoName) {
        // photoName은 "places/XXXX/photos/YYYY" 형태라 경로 변수로 넘기면 슬래시가 인코딩돼버린다.
        // 그래서 문자열로 미리 붙여 path에 그대로 심는다.
        PhotoMediaResponse response = restClient.get()
                .uri(uriBuilder -> uriBuilder.path("/v1/" + photoName + "/media")
                        .queryParam("maxWidthPx", MAX_PHOTO_WIDTH_PX)
                        .queryParam("key", properties.placesApiKey())
                        .queryParam("skipHttpRedirect", true)
                        .build())
                .retrieve()
                .body(PhotoMediaResponse.class);

        return response != null ? Optional.ofNullable(response.photoUri()) : Optional.empty();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record TextSearchResponse(List<Place> places) {

        @JsonIgnoreProperties(ignoreUnknown = true)
        record Place(List<Photo> photos) {
        }

        @JsonIgnoreProperties(ignoreUnknown = true)
        record Photo(String name) {
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record PhotoMediaResponse(String name, @JsonProperty("photoUri") String photoUri) {
    }
}
