package com.anything.momeogji.mydata.processing.place;

import com.anything.momeogji.config.KakaoProperties;
import com.anything.momeogji.mydata.retry.MyDataExternalCallRetryExecutor;
import com.anything.momeogji.mydata.retry.RetryableMyDataExternalCallException;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * 카카오 로컬의 키워드 장소 검색 API를 사용하는 {@link MerchantPlaceSearchClient} 구현체다.
 *
 * 가맹점 카테고리를 확인하기 위해 위치·반경 조건 없이 목적별 음식점·카페 그룹으로 정확도 순 첫 페이지를 조회한다.
 * 정상 검색 결과는 검색어와 카테고리 그룹별로 JVM 캐시에 보존한다. 외부 호출 실패나 비정상 응답은
 * 캐시에 남기지 않고 전체 마이데이터 가공을 중단시키지 않도록 기록한 뒤 빈 목록으로 대체한다.
 */
@Component
public class KakaoLocalKeywordSearchClient implements MerchantPlaceSearchClient {

    private static final Logger log = LoggerFactory.getLogger(KakaoLocalKeywordSearchClient.class);
    private static final String KEYWORD_SEARCH_PATH = "/v2/local/search/keyword.json";
    private static final int MAX_PAGE_SIZE = 15;
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(2);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(3);
    private static final Set<String> ALLOWED_CATEGORY_GROUP_CODES = Set.of("FD6", "CE7");

    private final RestClient restClient;
    private final Cache<SearchCacheKey, List<SearchCandidate>> searchCache;
    private final MyDataExternalCallRetryExecutor externalCallRetryExecutor;
    private final KakaoSearchCircuitBreaker circuitBreaker;

    /**
     * 프로젝트의 카카오 REST API 설정과 MyData 전용 검색 캐시 설정으로 Client를 생성한다.
     *
     * @param restClientBuilder Spring이 제공하는 REST 클라이언트 빌더
     * @param properties 카카오 REST API 키와 로컬 API 기본 주소
     * @param cacheProperties 카카오 장소 후보 캐시의 만료 시간과 최대 항목 수
     * @param externalCallRetryExecutor 일시적인 카카오 외부 실패를 한 번 재시도하는 실행기
     * @param circuitBreaker 연속 최종 실패 시 캐시 MISS 외부 요청을 제한하는 회로 차단기
     */
    public KakaoLocalKeywordSearchClient(
            RestClient.Builder restClientBuilder,
            KakaoProperties properties,
            KakaoSearchCacheProperties cacheProperties,
            MyDataExternalCallRetryExecutor externalCallRetryExecutor,
            KakaoSearchCircuitBreaker circuitBreaker
    ) {
        this.restClient = restClientBuilder
                .baseUrl(properties.baseUrl())
                .defaultHeader(
                        HttpHeaders.AUTHORIZATION,
                        "KakaoAK " + properties.restApiKey()
                )
                .requestFactory(createRequestFactory())
                .build();
        this.searchCache = Caffeine.newBuilder()
                .expireAfterWrite(cacheProperties.ttl())
                .maximumSize(cacheProperties.maximumSize())
                .build();
        this.externalCallRetryExecutor = externalCallRetryExecutor;
        this.circuitBreaker = circuitBreaker;
    }

    /**
     * 원본 또는 비교용 가맹점명을 카카오 로컬 키워드 검색어로 전달하고 첫 페이지 후보를 반환한다.
     *
     * <p>동일한 검색어와 그룹 코드의 정상 결과는 캐시에서 재사용한다. 정상 빈 응답도 캐시하지만
     * 외부 호출 실패나 응답 구조 누락은 캐시하지 않아 다음 호출에서 다시 조회할 수 있게 한다.</p>
     *
     * @param query 카카오 로컬 API에 전달할 가맹점명 검색어
     * @param categoryGroupCode 음식점 {@code FD6} 또는 카페 {@code CE7} 그룹 코드
     * @return 카카오 정확도 순서를 유지한 불변 후보 목록. 결과가 없거나 호출에 실패하면 빈 목록
     * @throws IllegalArgumentException 검색어가 null 또는 공백이거나 그룹 코드가 허용값이 아닌 경우
     */
    @Override
    public List<SearchCandidate> search(String query, String categoryGroupCode) {
        // 외부 API에 의미 없는 검색어를 전송하지 않도록 공개 경계에서 검증한다.
        if (query == null || query.isBlank()) {
            throw new IllegalArgumentException("query는 필수입니다.");
        }
        // MyData 목적 필터에서 지원하는 음식점·카페 그룹만 카카오 요청에 사용한다.
        if (!ALLOWED_CATEGORY_GROUP_CODES.contains(categoryGroupCode)) {
            throw new IllegalArgumentException("categoryGroupCode는 FD6 또는 CE7이어야 합니다.");
        }

        SearchCacheKey cacheKey = new SearchCacheKey(query, categoryGroupCode);

        // 회로가 열려 있어도 이미 검증·저장된 검색 결과는 외부 호출 없이 그대로 사용한다.
        List<SearchCandidate> cachedCandidates = searchCache.getIfPresent(cacheKey);
        if (cachedCandidates != null) {
            return cachedCandidates;
        }

        // 연속 외부 실패로 회로가 열렸다면 새로운 검색을 호출하지 않고 해당 가맹점만 제외한다.
        if (!circuitBreaker.allowRequest()) {
            return List.of();
        }

        try {
            // 동일 키의 동시 캐시 MISS는 한 로더로 병합하고 그 안에서 일시적 실패만 한 번 재시도한다.
            return searchCache.get(cacheKey, key -> {
                try {
                    List<SearchCandidate> candidates = externalCallRetryExecutor.execute(
                            "카카오 로컬 키워드 검색",
                            () -> requestCandidates(key)
                    );

                    // 정상적인 빈 장소 배열을 포함한 유효 응답은 연속 외부 실패 상태를 초기화한다.
                    circuitBreaker.recordSuccess();
                    return candidates;
                } catch (RetryableMyDataExternalCallException exception) {
                    // 같은 캐시 키를 기다리는 호출자가 여럿이어도 실제 실패한 로더 한 건만 기록한다.
                    circuitBreaker.recordFinalFailure();
                    throw exception;
                }
            });
        } catch (RetryableMyDataExternalCallException exception) {
            // 한 번의 재시도까지 실패한 외부 오류는 캐시에 남기지 않고 해당 가맹점만 제외한다.
            log.warn(
                    "카카오 로컬 가맹점 검색이 재시도 후에도 실패했습니다. "
                            + "query={}, categoryGroupCode={}, cause={}",
                    query,
                    categoryGroupCode,
                    exception.getMessage()
            );
            return List.of();
        } catch (RestClientException | IllegalStateException exception) {
            // 재시도 대상이 아닌 HTTP·역직렬화·응답 구조 오류는 즉시 해당 가맹점만 제외한다.
            log.warn(
                    "카카오 로컬 가맹점 검색에 실패했습니다. query={}, categoryGroupCode={}, cause={}",
                    query,
                    categoryGroupCode,
                    exception.getMessage()
            );
            return List.of();
        }
    }

    /**
     * 캐시에 없는 검색 키로 카카오 로컬 API를 호출하고 저장 가능한 불변 후보 목록을 생성한다.
     *
     * @param cacheKey 실제 요청 검색어와 음식점·카페 그룹을 포함한 캐시 키
     * @return 카카오 정확도 순서를 유지한 불변 후보 목록. 정상적인 결과 없음은 빈 목록
     * @throws RetryableMyDataExternalCallException 연결·타임아웃·429·5xx가 발생한 경우
     * @throws RestClientException 재시도 대상이 아닌 HTTP 오류 또는 응답 역직렬화에 실패한 경우
     * @throws IllegalStateException 응답 본문이나 장소 배열이 누락된 경우
     */
    private List<SearchCandidate> requestCandidates(SearchCacheKey cacheKey) {
        KakaoKeywordSearchResponse response;
        try {
            // 위치나 반경 조건 없이 요청 그룹 안에서 정확도 순 최대 15개 장소를 조회한다.
            response = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path(KEYWORD_SEARCH_PATH)
                            .queryParam("query", cacheKey.query())
                            .queryParam("category_group_code", cacheKey.categoryGroupCode())
                            .queryParam("size", MAX_PAGE_SIZE)
                            .queryParam("sort", "accuracy")
                            .build())
                    .retrieve()
                    .body(KakaoKeywordSearchResponse.class);
        } catch (ResourceAccessException exception) {
            // 연결 실패와 연결·읽기 타임아웃은 같은 요청으로 회복될 수 있는 외부 실패로 분류한다.
            throw new RetryableMyDataExternalCallException(
                    "카카오 로컬 API 연결 또는 응답 대기에 실패했습니다.",
                    exception
            );
        } catch (RestClientResponseException exception) {
            // 호출량 제한과 서버 오류만 일시적 외부 실패로 분류하고 나머지 4xx는 즉시 전달한다.
            if (exception.getStatusCode().value() == 429
                    || exception.getStatusCode().is5xxServerError()) {
                throw new RetryableMyDataExternalCallException(
                        "카카오 로컬 API가 일시적 HTTP 오류를 반환했습니다. status="
                                + exception.getStatusCode().value(),
                        exception
                );
            }
            throw exception;
        }

        // 정상 빈 배열과 구분할 수 없는 본문·필드 누락은 캐시하지 않도록 예외로 전달한다.
        if (response == null || response.documents() == null) {
            throw new IllegalStateException("카카오 로컬 검색 응답의 documents가 누락되었습니다.");
        }

        // 요청 그룹과 일치하고 장소명·세부 카테고리가 있는 응답만 후보로 변환한다.
        return response.documents().stream()
                .filter(Objects::nonNull)
                .filter(document -> hasRequiredPlaceInformation(
                        document,
                        cacheKey.categoryGroupCode()
                ))
                .map(KakaoLocalKeywordSearchClient::toSearchCandidate)
                .toList();
    }

    /**
     * 카카오 응답 항목이 요청한 그룹과 일치하며 후속 매칭에 필요한 장소명·세부 카테고리를 갖는지 확인한다.
     *
     * @param document 카카오 키워드 검색의 개별 장소 응답
     * @param requestedCategoryGroupCode 요청에 사용한 음식점 또는 카페 그룹 코드
     * @return 요청 그룹·장소명·세부 카테고리 조건을 모두 충족하면 true
     */
    private static boolean hasRequiredPlaceInformation(
            KakaoKeywordSearchResponse.Document document,
            String requestedCategoryGroupCode
    ) {
        return requestedCategoryGroupCode.equals(document.categoryGroupCode())
                && document.placeName() != null
                && !document.placeName().isBlank()
                && document.categoryName() != null
                && !document.categoryName().isBlank();
    }

    /**
     * 카카오 장소 응답을 분류 계층이 외부 제공자와 무관하게 사용할 검색 후보로 변환한다.
     *
     * @param document 필수 장소 정보가 확인된 카카오 장소 응답
     * @return 장소명·카테고리 그룹 코드·전체 세부 카테고리를 담은 검색 후보
     */
    private static SearchCandidate toSearchCandidate(KakaoKeywordSearchResponse.Document document) {
        return new SearchCandidate(
                document.placeName(),
                document.categoryGroupCode(),
                document.categoryName()
        );
    }

    /**
     * 카카오 연결에는 2초, 응답 대기에는 3초 제한시간이 적용된 요청 팩토리를 만든다.
     *
     * @return 연결·읽기 제한시간을 설정한 Spring HTTP 요청 팩토리
     */
    private static ClientHttpRequestFactory createRequestFactory() {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(CONNECT_TIMEOUT)
                .build();
        JdkClientHttpRequestFactory requestFactory =
                new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(READ_TIMEOUT);
        return requestFactory;
    }

    /**
     * 사용자·모임·결제 정보 없이 실제 카카오 검색 요청을 구분하는 JVM 캐시 키다.
     *
     * @param query 원본 또는 비교용 가맹점명 검색어
     * @param categoryGroupCode 음식점 {@code FD6} 또는 카페 {@code CE7} 그룹 코드
     */
    private record SearchCacheKey(String query, String categoryGroupCode) {
    }

    /**
     * 카카오 키워드 장소 검색 응답 중 장소 배열만 역직렬화하는 내부 DTO다.
     *
     * @param documents 정확도 순으로 회신된 장소 목록
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    private record KakaoKeywordSearchResponse(List<Document> documents) {

        /**
         * 카카오 장소 응답 중 장소명 매칭과 세부 음식 카테고리 보존에 필요한 필드만 받는 내부 DTO다.
         *
         * @param placeName 카카오에 등록된 장소명
         * @param categoryGroupCode 카카오 중요 카테고리 그룹 코드
         * @param categoryName 카카오 전체 세부 카테고리 경로
         */
        @JsonIgnoreProperties(ignoreUnknown = true)
        private record Document(
                @JsonProperty("place_name") String placeName,
                @JsonProperty("category_group_code") String categoryGroupCode,
                @JsonProperty("category_name") String categoryName
        ) {
        }
    }
}
