package com.anything.momeogji.mydata.processing.place;

import com.anything.momeogji.config.KakaoProperties;
import com.anything.momeogji.mydata.processing.place.MerchantPlaceSearchClient.SearchCandidate;
import com.anything.momeogji.mydata.retry.MyDataExternalCallRetryExecutor;
import com.anything.momeogji.mydata.retry.MyDataRecoveryProperties;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 카카오 키워드 검색 Client의 Caffeine 키 분리, 정상 결과 재사용과 실패 미저장 정책을 검증한다.
 *
 * <p>실제 카카오 서버와 API 키를 사용하지 않고 테스트 프로세스 안의 HTTP 서버로 응답과
 * 요청 횟수를 통제한다.</p>
 */
class KakaoLocalKeywordSearchClientTest {

    private static final String SEARCH_PATH = "/v2/local/search/keyword.json";

    private final AtomicInteger requestCount = new AtomicInteger();
    private final Queue<MockResponse> responses = new ArrayDeque<>();

    private HttpServer mockServer;

    /**
     * 각 테스트가 독립적인 요청 횟수와 응답 순서를 사용하도록 임의 포트의 HTTP 서버를 시작한다.
     *
     * @throws IOException 테스트 HTTP 서버를 열 수 없는 경우
     */
    @BeforeEach
    void setUp() throws IOException {
        mockServer = HttpServer.create(
                new InetSocketAddress(InetAddress.getLoopbackAddress(), 0),
                0
        );
        mockServer.createContext(SEARCH_PATH, this::handleSearchRequest);
        mockServer.start();
    }

    /**
     * 테스트가 끝나면 HTTP 서버를 즉시 종료해 포트와 스레드 자원을 반환한다.
     */
    @AfterEach
    void tearDown() {
        mockServer.stop(0);
    }

    /**
     * 동일한 검색어와 그룹 코드의 정상 결과를 한 번만 조회하고 불변 목록으로 재사용하는지 확인한다.
     */
    @Test
    void 동일한_검색키의_정상_결과를_캐시한다() {
        responses.add(successResponse("FD6", "영인성", "음식점 > 중식 > 중화요리"));
        KakaoLocalKeywordSearchClient client = createClient(Duration.ofMinutes(10), 1000);

        List<SearchCandidate> firstResult = client.search("영인성", "FD6");
        List<SearchCandidate> secondResult = client.search("영인성", "FD6");

        assertThat(requestCount).hasValue(1);
        assertThat(secondResult).isSameAs(firstResult);
        assertThat(firstResult)
                .extracting(SearchCandidate::placeName)
                .containsExactly("영인성");
        assertThatThrownBy(() -> firstResult.add(
                new SearchCandidate("추가 장소", "FD6", "음식점 > 한식")
        )).isInstanceOf(UnsupportedOperationException.class);
    }

    /**
     * 검색어 또는 카테고리 그룹이 다르면 서로 다른 캐시 항목으로 카카오 요청을 수행하는지 확인한다.
     */
    @Test
    void 검색어와_그룹코드를_각각_캐시키로_구분한다() {
        responses.add(successResponse("FD6", "영인성", "음식점 > 중식 > 중화요리"));
        responses.add(successResponse("CE7", "영인성 카페", "카페 > 커피전문점"));
        responses.add(successResponse("FD6", "영인성 본점", "음식점 > 중식 > 중화요리"));
        KakaoLocalKeywordSearchClient client = createClient(Duration.ofMinutes(10), 1000);

        client.search("영인성", "FD6");
        client.search("영인성", "CE7");
        client.search("영인성본점", "FD6");

        // 세 키를 다시 조회했을 때 추가 HTTP 호출 없이 각각의 캐시 결과를 사용한다.
        client.search("영인성", "FD6");
        client.search("영인성", "CE7");
        client.search("영인성본점", "FD6");

        assertThat(requestCount).hasValue(3);
    }

    /**
     * 카카오가 정상적으로 빈 장소 배열을 회신하면 같은 검색의 반복 요청을 막는지 확인한다.
     */
    @Test
    void 정상적인_빈_검색결과도_캐시한다() {
        responses.add(new MockResponse(200, "{\"documents\":[]}"));
        KakaoLocalKeywordSearchClient client = createClient(Duration.ofMinutes(10), 1000);

        assertThat(client.search("등록되지 않은 가맹점", "FD6")).isEmpty();
        assertThat(client.search("등록되지 않은 가맹점", "FD6")).isEmpty();

        assertThat(requestCount).hasValue(1);
    }

    /**
     * 저장 후 TTL이 지나면 만료된 장소 후보를 사용하지 않고 카카오 API를 다시 호출하는지 확인한다.
     *
     * @throws InterruptedException 테스트 스레드의 짧은 만료 대기가 중단된 경우
     */
    @Test
    void 저장후_TTL이_지나면_다시_조회한다() throws InterruptedException {
        responses.add(successResponse("FD6", "영인성", "음식점 > 중식 > 중화요리"));
        responses.add(successResponse("FD6", "영인성", "음식점 > 중식 > 중화요리"));
        KakaoLocalKeywordSearchClient client = createClient(Duration.ofMillis(10), 1000);

        client.search("영인성", "FD6");
        Thread.sleep(50);
        client.search("영인성", "FD6");

        assertThat(requestCount).hasValue(2);
    }

    /**
     * 일시적인 HTTP 서버 오류는 같은 검색을 한 번 재시도하고 성공 결과를 캐시하는지 확인한다.
     */
    @Test
    void 일시적_HTTP오류는_한번_재시도하고_성공결과를_캐시한다() {
        responses.add(new MockResponse(500, "{\"message\":\"temporary error\"}"));
        responses.add(successResponse("FD6", "영인성", "음식점 > 중식 > 중화요리"));
        KakaoLocalKeywordSearchClient client = createClient(Duration.ofMinutes(10), 1000);

        assertThat(client.search("영인성", "FD6"))
                .extracting(SearchCandidate::placeName)
                .containsExactly("영인성");

        // 재시도 성공 결과가 캐시되므로 두 번째 검색에서는 HTTP 호출이 늘지 않는다.
        client.search("영인성", "FD6");
        assertThat(requestCount).hasValue(2);
    }

    /**
     * HTTP 429 호출량 제한도 서버 오류와 동일하게 한 번 재시도하는지 확인한다.
     */
    @Test
    void HTTP_429응답은_한번_재시도한다() {
        responses.add(new MockResponse(429, "{\"message\":\"too many requests\"}"));
        responses.add(successResponse("CE7", "테스트 카페", "카페 > 커피전문점"));
        KakaoLocalKeywordSearchClient client = createClient(Duration.ofMinutes(10), 1000);

        assertThat(client.search("테스트 카페", "CE7"))
                .extracting(SearchCandidate::placeName)
                .containsExactly("테스트 카페");
        assertThat(requestCount).hasValue(2);
    }

    /**
     * 한 번의 재시도까지 실패한 결과를 캐시하지 않아 다음 검색에서 새 호출을 수행하는지 확인한다.
     */
    @Test
    void 재시도까지_실패한_결과는_캐시하지_않는다() {
        responses.add(new MockResponse(500, "{\"message\":\"temporary error\"}"));
        responses.add(new MockResponse(500, "{\"message\":\"temporary error\"}"));
        responses.add(successResponse("FD6", "영인성", "음식점 > 중식 > 중화요리"));
        KakaoLocalKeywordSearchClient client = createClient(Duration.ofMinutes(10), 1000);

        assertThat(client.search("영인성", "FD6")).isEmpty();
        assertThat(client.search("영인성", "FD6"))
                .extracting(SearchCandidate::placeName)
                .containsExactly("영인성");

        assertThat(requestCount).hasValue(3);
    }

    /**
     * 재시도 대상이 아닌 일반 4xx 응답은 추가 호출 없이 즉시 해당 검색만 제외하는지 확인한다.
     */
    @Test
    void 일반_4xx응답은_재시도하지_않는다() {
        responses.add(new MockResponse(400, "{\"message\":\"bad request\"}"));
        responses.add(successResponse("FD6", "영인성", "음식점 > 중식 > 중화요리"));
        KakaoLocalKeywordSearchClient client = createClient(Duration.ofMinutes(10), 1000);

        assertThat(client.search("영인성", "FD6")).isEmpty();
        assertThat(requestCount).hasValue(1);

        assertThat(client.search("영인성", "FD6")).hasSize(1);
        assertThat(requestCount).hasValue(2);
    }

    /**
     * 응답 장소 배열 누락을 정상 빈 결과와 구분해 다음 요청에서 다시 조회하는지 확인한다.
     */
    @Test
    void documents가_누락된_응답은_캐시하지_않는다() {
        responses.add(new MockResponse(200, "{}"));
        responses.add(successResponse("FD6", "영인성", "음식점 > 중식 > 중화요리"));
        KakaoLocalKeywordSearchClient client = createClient(Duration.ofMinutes(10), 1000);

        assertThat(client.search("영인성", "FD6")).isEmpty();
        assertThat(client.search("영인성", "FD6")).hasSize(1);

        assertThat(requestCount).hasValue(2);
    }

    /**
     * 캐시가 무제한 또는 즉시 만료 상태로 시작되지 않도록 잘못된 설정을 거부하는지 확인한다.
     */
    @Test
    void 잘못된_캐시_설정을_거부한다() {
        assertThatThrownBy(() -> new KakaoSearchCacheProperties(null, 1000))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new KakaoSearchCacheProperties(Duration.ZERO, 1000))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new KakaoSearchCacheProperties(Duration.ofMinutes(10), 0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    /**
     * 연속 최종 외부 실패가 임계값에 도달하면 캐시 MISS를 차단하되 기존 캐시 HIT는 제공하는지 확인한다.
     *
     * @throws InterruptedException 짧은 회로 OPEN 시간 대기가 중단된 경우
     */
    @Test
    void 연속_최종실패_세건이면_회로를_열고_만료후_다시_호출한다() throws InterruptedException {
        responses.add(successResponse("FD6", "캐시 장소", "음식점 > 한식"));
        for (int index = 0; index < 6; index++) {
            responses.add(new MockResponse(500, "{\"message\":\"temporary error\"}"));
        }
        responses.add(successResponse("FD6", "복구 장소", "음식점 > 한식"));

        KakaoLocalKeywordSearchClient client = createClient(
                Duration.ofMinutes(10),
                1000,
                3,
                Duration.ofMillis(30)
        );

        assertThat(client.search("캐시 장소", "FD6")).hasSize(1);
        assertThat(client.search("실패 장소 1", "FD6")).isEmpty();
        assertThat(client.search("실패 장소 2", "FD6")).isEmpty();
        assertThat(client.search("실패 장소 3", "FD6")).isEmpty();
        assertThat(requestCount).hasValue(7);

        // 열린 회로에서도 기존 캐시는 사용하지만 새 검색은 외부 요청 없이 제외한다.
        assertThat(client.search("캐시 장소", "FD6")).hasSize(1);
        assertThat(client.search("차단 장소", "FD6")).isEmpty();
        assertThat(requestCount).hasValue(7);

        Thread.sleep(50);

        assertThat(client.search("복구 장소", "FD6")).hasSize(1);
        assertThat(requestCount).hasValue(8);
    }

    /**
     * 테스트 서버 주소와 지정한 캐시 정책을 사용하는 실제 검색 Client를 생성한다.
     *
     * @param ttl 테스트에서 적용할 저장 후 만료 시간
     * @param maximumSize 테스트에서 적용할 최대 검색 키 개수
     * @return 로컬 HTTP 서버를 카카오 API 대신 호출하는 검색 Client
     */
    private KakaoLocalKeywordSearchClient createClient(Duration ttl, long maximumSize) {
        return createClient(
                ttl,
                maximumSize,
                3,
                Duration.ofSeconds(1)
        );
    }

    /**
     * 테스트별 회로 실패 임계값과 OPEN 시간을 적용한 검색 Client를 생성한다.
     *
     * @param ttl 테스트에서 적용할 캐시 만료 시간
     * @param maximumSize 테스트에서 적용할 최대 캐시 키 개수
     * @param failureThreshold 회로를 열 연속 최종 실패 횟수
     * @param openDuration 외부 호출을 차단할 회로 OPEN 시간
     * @return 로컬 HTTP 서버와 짧은 재시도 간격을 사용하는 검색 Client
     */
    private KakaoLocalKeywordSearchClient createClient(
            Duration ttl,
            long maximumSize,
            int failureThreshold,
            Duration openDuration
    ) {
        String baseUrl = "http://"
                + mockServer.getAddress().getHostString()
                + ":"
                + mockServer.getAddress().getPort();
        KakaoProperties kakaoProperties = new KakaoProperties(
                "test-rest-api-key",
                baseUrl,
                "http://localhost/callback"
        );
        MyDataRecoveryProperties recoveryProperties = new MyDataRecoveryProperties(
                Duration.ofMillis(1),
                failureThreshold,
                openDuration
        );

        return new KakaoLocalKeywordSearchClient(
                RestClient.builder(),
                kakaoProperties,
                new KakaoSearchCacheProperties(ttl, maximumSize),
                new MyDataExternalCallRetryExecutor(recoveryProperties),
                new KakaoSearchCircuitBreaker(recoveryProperties)
        );
    }

    /**
     * 테스트가 준비한 응답을 순서대로 반환하고 실제 외부 호출 횟수를 기록한다.
     *
     * @param exchange 테스트 검색 Client가 보낸 HTTP 요청과 응답 통로
     * @throws IOException 응답 본문을 전송할 수 없는 경우
     */
    private void handleSearchRequest(HttpExchange exchange) throws IOException {
        requestCount.incrementAndGet();
        MockResponse response = responses.poll();
        if (response == null) {
            response = new MockResponse(500, "{\"message\":\"missing mock response\"}");
        }

        byte[] responseBody = response.body().getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set(
                "Content-Type",
                "application/json; charset=UTF-8"
        );
        exchange.sendResponseHeaders(response.statusCode(), responseBody.length);
        try (var outputStream = exchange.getResponseBody()) {
            outputStream.write(responseBody);
        }
    }

    /**
     * 장소 한 건을 포함한 카카오 키워드 검색 형식의 정상 응답을 만든다.
     *
     * @param categoryGroupCode 응답 장소의 음식점 또는 카페 그룹 코드
     * @param placeName 카카오 장소명
     * @param categoryName 카카오 전체 카테고리 경로
     * @return HTTP 200 상태와 장소 한 건을 가진 테스트 응답
     */
    private MockResponse successResponse(
            String categoryGroupCode,
            String placeName,
            String categoryName
    ) {
        String responseBody = """
                {
                  "documents": [
                    {
                      "place_name": "%s",
                      "category_group_code": "%s",
                      "category_name": "%s"
                    }
                  ]
                }
                """.formatted(placeName, categoryGroupCode, categoryName);
        return new MockResponse(200, responseBody);
    }

    /**
     * 로컬 HTTP 서버가 순서대로 반환할 상태 코드와 JSON 본문을 보존한다.
     *
     * @param statusCode HTTP 응답 상태 코드
     * @param body UTF-8 JSON 응답 본문
     */
    private record MockResponse(int statusCode, String body) {
    }
}
