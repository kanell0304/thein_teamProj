package com.anything.momeogji.mydata.transform.local;

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
import java.util.Objects;

/**
 * 카카오 로컬의 키워드 장소 검색 API를 사용하는 {@link MerchantPlaceSearchClient} 구현체다.
 *
 * 가맹점 카테고리를 확인하기 위해 위치·반경·카테고리 필터 없이 정확도 순 첫 페이지를 조회한다.
 * 외부 호출 실패는 전체 마이데이터 가공을 중단시키지 않도록 기록한 뒤 빈 목록으로 대체한다.
 */
@Component
public class KakaoLocalKeywordSearchClient implements MerchantPlaceSearchClient {

    private static final Logger log = LoggerFactory.getLogger(KakaoLocalKeywordSearchClient.class);
    private static final String KEYWORD_SEARCH_PATH = "/v2/local/search/keyword.json";
    private static final int MAX_PAGE_SIZE = 15;
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(5);

    private final RestClient restClient;

    /**
     * 프로젝트의 카카오 REST API 설정으로 인증 헤더와 요청 제한시간이 적용된 클라이언트를 생성한다.
     *
     * @param restClientBuilder Spring이 제공하는 REST 클라이언트 빌더
     * @param properties 카카오 REST API 키와 로컬 API 기본 주소
     */
    public KakaoLocalKeywordSearchClient(
            RestClient.Builder restClientBuilder,
            KakaoProperties properties
    ) {
        this.restClient = restClientBuilder
                .baseUrl(properties.baseUrl())
                .defaultHeader(
                        HttpHeaders.AUTHORIZATION,
                        "KakaoAK " + properties.restApiKey()
                )
                .requestFactory(createRequestFactory())
                .build();
    }

    /**
     * 원본 또는 비교용 가맹점명을 카카오 로컬 키워드 검색어로 전달하고 첫 페이지 후보를 반환한다.
     *
     * @param query 카카오 로컬 API에 전달할 가맹점명 검색어
     * @return 카카오 정확도 순서를 유지한 불변 후보 목록. 결과가 없거나 호출에 실패하면 빈 목록
     * @throws IllegalArgumentException 검색어가 null 또는 공백인 경우
     */
    @Override
    public List<SearchCandidate> search(String query) {
        // 외부 API에 의미 없는 검색어를 전송하지 않도록 공개 경계에서 검증한다.
        if (query == null || query.isBlank()) {
            throw new IllegalArgumentException("query는 필수입니다.");
        }

        try {
            // 위치나 카테고리 제한 없이 정확도 순 최대 15개 장소를 조회한다.
            KakaoKeywordSearchResponse response = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path(KEYWORD_SEARCH_PATH)
                            .queryParam("query", query)
                            .queryParam("size", MAX_PAGE_SIZE)
                            .queryParam("sort", "accuracy")
                            .build())
                    .retrieve()
                    .body(KakaoKeywordSearchResponse.class);

            // 카카오가 본문이나 장소 배열을 회신하지 않으면 검색 결과 없음으로 처리한다.
            if (response == null || response.documents() == null) {
                return List.of();
            }

            // 장소 ID와 이름이 있는 응답만 중립 검색 후보로 변환하고 원본 응답 순서를 유지한다.
            return response.documents().stream()
                    .filter(Objects::nonNull)
                    .filter(KakaoLocalKeywordSearchClient::hasRequiredPlaceInformation)
                    .map(KakaoLocalKeywordSearchClient::toSearchCandidate)
                    .toList();
        } catch (RestClientException exception) {
            // 한 가맹점의 외부 검색 실패가 참가자 전체 데이터 가공 실패로 번지지 않도록 빈 결과로 대체한다.
            log.warn(
                    "카카오 로컬 가맹점 검색에 실패했습니다. query={}, cause={}",
                    query,
                    exception.getMessage()
            );
            return List.of();
        }
    }

    /**
     * 카카오 응답 항목에 후속 매칭에 필요한 장소 ID와 장소명이 있는지 확인한다.
     *
     * @param document 카카오 키워드 검색의 개별 장소 응답
     * @return 장소 ID와 장소명이 모두 존재하고 공백이 아니면 true
     */
    private static boolean hasRequiredPlaceInformation(KakaoKeywordSearchResponse.Document document) {
        return document.id() != null
                && !document.id().isBlank()
                && document.placeName() != null
                && !document.placeName().isBlank();
    }

    /**
     * 카카오 장소 응답을 분류 계층이 외부 제공자와 무관하게 사용할 검색 후보로 변환한다.
     *
     * @param document 필수 장소 정보가 확인된 카카오 장소 응답
     * @return 장소 ID·이름·카테고리·좌표 원본 문자열을 담은 검색 후보
     */
    private static SearchCandidate toSearchCandidate(KakaoKeywordSearchResponse.Document document) {
        return new SearchCandidate(
                document.id(),
                document.placeName(),
                document.categoryGroupCode(),
                document.x(),
                document.y()
        );
    }

    /**
     * 카카오 연결과 응답 대기가 무기한 지속되지 않도록 공통 5초 제한시간이 적용된 요청 팩토리를 만든다.
     *
     * @return 연결·읽기 제한시간을 설정한 Spring HTTP 요청 팩토리
     */
    private static ClientHttpRequestFactory createRequestFactory() {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(REQUEST_TIMEOUT)
                .build();
        JdkClientHttpRequestFactory requestFactory =
                new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(REQUEST_TIMEOUT);
        return requestFactory;
    }

    /**
     * 카카오 키워드 장소 검색 응답 중 장소 배열만 역직렬화하는 내부 DTO다.
     *
     * @param documents 정확도 순으로 회신된 장소 목록
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    private record KakaoKeywordSearchResponse(List<Document> documents) {

        /**
         * 카카오 장소 응답 중 가맹점 분류와 위치 보강에 필요한 필드만 받는 내부 DTO다.
         *
         * @param id 카카오 장소 ID
         * @param placeName 카카오에 등록된 장소명
         * @param categoryGroupCode 카카오 중요 카테고리 그룹 코드
         * @param x 경도 원본 문자열
         * @param y 위도 원본 문자열
         */
        @JsonIgnoreProperties(ignoreUnknown = true)
        private record Document(
                String id,
                @JsonProperty("place_name") String placeName,
                @JsonProperty("category_group_code") String categoryGroupCode,
                String x,
                String y
        ) {
        }
    }
}
