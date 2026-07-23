package com.anything.momeogji.mydata;

import com.anything.momeogji.mydata.collection.MyDataProvider;
import com.anything.momeogji.mydata.collection.cardapproval.CardApprovalParser;
import com.anything.momeogji.mydata.collection.cardapproval.CardApprovalResponse;
import com.anything.momeogji.mydata.collection.cardapproval.CardApprovalValidator;
import com.anything.momeogji.mydata.collection.cardlist.ConsentedCardIdSelector;
import com.anything.momeogji.mydata.collection.cardlist.CardListResponse;
import com.anything.momeogji.mydata.collection.cardlist.CardListValidator;
import com.anything.momeogji.mydata.collection.model.CardApprovalData;
import com.anything.momeogji.mydata.collection.model.CollectedUserMyData;
import com.anything.momeogji.mydata.processing.MyDataPipeline;
import com.anything.momeogji.mydata.processing.model.MyDataRestaurantData;
import com.anything.momeogji.mydata.retry.MyDataExternalCallRetryExecutor;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 동의한 참가자 한 명의 카드 마이데이터를 수집하고 최종 가공 흐름을 시작하는 동기식 핵심 서비스다.
 *
 * 카드 목록에서 전송에 동의한 카드 ID를 추출한 뒤 카드별 국내 승인내역을 조회·검증·파싱한다.
 * Dummy와 실제 API 중 어떤 데이터 제공 방식을 사용하는지는 {@link MyDataProvider} 구현체가 결정하며,
 * 이 서비스는 동일한 수집 흐름만 담당
 *
 * 카드 목록과 승인내역의 모든 페이지를 순서대로 처리한다.
 * 처리 중 한 단계라도 실패하면 부분 결과를 반환하지 않고 예외를 전달한다.
 * {@link #process(Long, LocalTime, String)}를 호출하면 수집 결과와 옵션 계층에서 받은 선택 시각·목적을
 * {@link MyDataPipeline}에 전달한다.
 * 참가자 단위 비동기 실행, 동의 여부 판단과 실패 상태 기록은 이후 이 서비스를 호출하는 상위 계층이 담당한다.
 */
@Service
public class MyDataService {

    private static final String FIRST_SEARCH_TIMESTAMP = "0";
    private static final int PAGE_LIMIT = 500;
    private static final String RESTAURANT_CATEGORY_GROUP_CODE = "FD6";
    private static final String CAFE_CATEGORY_GROUP_CODE = "CE7";
    private static final Set<String> CAFE_PURPOSES = Set.of("카페", "디저트");

    private final ObjectMapper objectMapper;
    private final MyDataProvider myDataProvider;
    private final CardListValidator cardListValidator;
    private final ConsentedCardIdSelector consentedCardIdSelector;
    private final CardApprovalValidator cardApprovalValidator;
    private final CardApprovalParser cardApprovalParser;
    private final MyDataPipeline myDataPipeline;
    private final MyDataExternalCallRetryExecutor externalCallRetryExecutor;

    /**
     * 마이데이터 수집과 최종 가공에 필요한 Provider와 단계별 처리 구성요소를 주입받는다.
     *
     * @param objectMapper Raw JSON을 응답 DTO로 역직렬화하는 Jackson 매퍼
     * @param myDataProvider Dummy 또는 실제 API에서 Raw JSON을 가져오는 Provider
     * @param cardListValidator 카드 목록 응답 검증기
     * @param consentedCardIdSelector 동의 카드 ID 추출기
     * @param cardApprovalValidator 국내 승인내역 응답 검증기
     * @param cardApprovalParser 국내 승인내역 내부 모델 변환기
     * @param myDataPipeline 수집된 참가자 마이데이터를 최종 가맹점 분류 결과로 가공하는 컴포넌트
     * @param externalCallRetryExecutor 일시적인 외부 Provider 실패를 요청 단위로 한 번 재시도하는 실행기
     */
    public MyDataService(
            ObjectMapper objectMapper,
            MyDataProvider myDataProvider,
            CardListValidator cardListValidator,
            ConsentedCardIdSelector consentedCardIdSelector,
            CardApprovalValidator cardApprovalValidator,
            CardApprovalParser cardApprovalParser,
            MyDataPipeline myDataPipeline,
            MyDataExternalCallRetryExecutor externalCallRetryExecutor
    ) {
        this.objectMapper = objectMapper;
        this.myDataProvider = myDataProvider;
        this.cardListValidator = cardListValidator;
        this.consentedCardIdSelector = consentedCardIdSelector;
        this.cardApprovalValidator = cardApprovalValidator;
        this.cardApprovalParser = cardApprovalParser;
        this.myDataPipeline = myDataPipeline;
        this.externalCallRetryExecutor = externalCallRetryExecutor;
    }

    /**
     * 지정한 사용자의 동의 카드와 국내 승인내역을 수집해 하나의 결과로 반환한다.
     *
     * 정상적으로 조회했지만 동의 카드 또는 승인내역이 없으면 사용자 ID와 빈 승인내역 목록을 가진 {@link CollectedUserMyData}를 반환
     * Provider 호출, JSON 역직렬화, 응답 검증 또는 파싱이 실패하면 부분 결과를 반환하지 않는다.
     *
     * @param userId 마이데이터를 제공한 사용자를 식별하는 내부 ID
     * @return 사용자 ID와 모든 동의 카드의 정제된 승인내역을 포함하는 불변 결과
     * @throws IllegalArgumentException 사용자 ID가 없거나 0 이하인 경우
     * @throws IllegalStateException JSON 역직렬화 또는 페이지 반복 상태가 올바르지 않은 경우
     */
    public CollectedUserMyData collect(Long userId) {
        // 수집 시작 전에 사용자 ID가 Dummy 경로와 내부 결과에 사용할 수 있는 양수인지 검사한다.
        if (userId == null || userId <= 0) {
            throw new IllegalArgumentException("userId는 1 이상이어야 합니다.");
        }

        // 한 번의 수집에 포함된 모든 카드와 페이지가 동일한 조회 종료일을 사용하도록 당일을 한 번만 계산한다.
        // 국내 승인내역 조회 시작일을 수집 당일 기준 1년 전으로 계산한다.
        LocalDate toDate = LocalDate.now();
        LocalDate fromDate = toDate.minusYears(1);

        // 카드 목록의 모든 페이지를 처리해 전송에 동의한 카드 ID만 원본 순서대로 수집한다.
        List<String> consentedCardIds = collectConsentedCardIds(userId);

        // 동의 카드 순서대로 국내 승인내역을 수집해 하나의 사용자 결과 목록으로 합친다.
        List<CardApprovalData> approvals = new ArrayList<>();
        for (String cardId : consentedCardIds) {
            approvals.addAll(collectCardApprovals(userId, cardId, fromDate, toDate));
        }

        // 정상적인 빈 결과를 포함해 사용자 ID와 승인내역을 불변 결과로 반환한다.
        return new CollectedUserMyData(userId, approvals);
    }

    /**
     * 지정한 사용자의 마이데이터를 수집한 뒤 선택 시각이 속한 시간대의 최종 가맹점 분류 결과까지 가공한다.
     *
     * <p>마이데이터 제공에 동의한 참가자에 대해서만 상위 계층이 이 메서드를 호출해야 한다.
     * 선택 시각이 잘못된 경우 카드 목록이나 외부 장소 API를 호출하기 전에 즉시 실패한다.</p>
     *
     * <p>카드나 승인내역이 없거나 선택 시각이 속한 시간대에 해당하는 결제가 없으면
     * 빈 음식점 목록을 정상 반환한다. 수집·정제·집계 중 발생한 데이터 오류는
     * 부분 결과로 바꾸지 않고 호출자에게 전달한다.</p>
     *
     * @param userId 마이데이터를 제공한 사용자를 식별하는 내부 ID
     * @param meetingTime 옵션 계층에서 검증한 마이데이터 필터 기준 시각
     * @param purpose 모임에서 선택한 목적. 카페·디저트는 카페, 나머지는 음식점 검색에 사용
     * @return 카카오 장소명과 음식 카테고리만 포함한 불변 음식점 목록
     * @throws IllegalArgumentException 선택 시각이 없거나 사용자 ID가 올바르지 않은 경우
     * @throws IllegalStateException 마이데이터 수집·역직렬화·페이지 처리에 실패한 경우
     */
    public List<MyDataRestaurantData> process(
            Long userId,
            LocalTime meetingTime,
            String purpose
    ) {
        // 잘못된 요청에서 카드 목록 수집 비용이 발생하지 않도록 선택 시각을 먼저 검증한다.
        if (meetingTime == null) {
            throw new IllegalArgumentException("meetingTime은 필수입니다.");
        }

        // 잘못된 목적에서 카드 수집 비용이 발생하지 않도록 목적을 먼저 그룹 코드로 변환한다.
        String categoryGroupCode = resolveCategoryGroupCode(purpose);

        // 기존 수집 흐름을 한 번만 호출해 사용자의 모든 동의 카드 승인내역을 가져온다.
        CollectedUserMyData userMyData = collect(userId);

        // 수집 결과와 일회성 필터 시각·목적 그룹을 정제·집계·가맹점 분류 파이프라인에 전달한다.
        return myDataPipeline.execute(
                userMyData,
                meetingTime,
                categoryGroupCode
        );
    }

    /**
     * 모임 목적을 카카오 키워드 검색에서 사용할 음식점 또는 카페 그룹 코드로 변환한다.
     *
     * @param purpose Meetup에 저장된 모임 목적 문자열
     * @return 카페·디저트면 {@code CE7}, 그 외 유효한 목적이면 {@code FD6}
     * @throws IllegalArgumentException 목적이 null 또는 공백인 경우
     */
    private String resolveCategoryGroupCode(String purpose) {
        // 목적은 카드 수집과 외부 장소 검색의 필수 조건이므로 미입력 값을 거부한다.
        if (purpose == null || purpose.isBlank()) {
            throw new IllegalArgumentException("purpose는 필수입니다.");
        }

        // UI에서 선택한 목적의 앞뒤 공백을 제거해 고정된 카페 관련 옵션과 비교한다.
        String normalizedPurpose = purpose.strip();
        return CAFE_PURPOSES.contains(normalizedPurpose)
                ? CAFE_CATEGORY_GROUP_CODE
                : RESTAURANT_CATEGORY_GROUP_CODE;
    }

    /**
     * 카드 목록의 모든 페이지를 조회하고 전송에 동의한 카드 ID를 수집한다.
     *
     * 최초 페이지는 조회 타임스탬프 {@code 0}으로 요청하고, 이후 페이지는 직전 응답의 {@code next_page}만 사용
     * 페이지 간 카드 ID 중복과 페이지 토큰 반복은 정상 응답으로 보지 않는다.
     *
     * @param userId 카드 목록을 조회할 사용자 내부 ID
     * @return 응답에 처음 등장한 순서를 유지한 동의 카드 ID 불변 목록
     */
    private List<String> collectConsentedCardIds(Long userId) {
        List<String> consentedCardIds = new ArrayList<>();
        Set<String> visitedCardIds = new HashSet<>();
        Set<String> visitedNextPages = new HashSet<>();
        String nextPage = null;

        while (true) {
            // 최초 페이지에는 0을 전달하고 다음 페이지 요청에서는 search_timestamp를 제외한다.
            String searchTimestamp = nextPage == null ? FIRST_SEARCH_TIMESTAMP : null;
            String requestNextPage = nextPage;

            // 현재 페이지 조건의 외부 Provider 요청만 일시적 실패 시 한 번 재시도한다.
            String rawJson = externalCallRetryExecutor.execute(
                    "카드 목록 조회",
                    () -> myDataProvider.fetchCardListRawJson(
                            userId,
                            searchTimestamp,
                            requestNextPage,
                            PAGE_LIMIT
                    )
            );

            // 카드 목록 Raw JSON을 응답 DTO로 역직렬화한다.
            CardListResponse response = deserialize(rawJson, CardListResponse.class,
                    "카드 목록 응답(userId=" + userId  + ", nextPage=" +
                            (requestNextPage == null ? "FIRST" : requestNextPage) + ")");

            // 동의 카드 추출 전에 현재 페이지의 응답 상태와 필드 규칙을 검증한다.
            cardListValidator.validate(response);

            // 카드 목록 전체를 기준으로 페이지 사이에 동일 card_id가 다시 나타나는지 검사한다.
            for (CardListResponse.CardItem card : response.cards()) {
                if (!visitedCardIds.add(card.cardId())) {
                    throw new IllegalStateException(
                            "카드 목록 페이지 사이에 중복된 card_id가 있습니다: " + card.cardId()
                    );
                }
            }

            // 검증된 현재 페이지에서 전송에 동의한 카드 ID만 추출한다.
            List<String> currentPageCardIds = consentedCardIdSelector.selectCardIds(response);

            // 현재 페이지의 동의 카드 ID를 카드 목록의 원본 순서대로 전체 결과에 추가한다.
            consentedCardIds.addAll(currentPageCardIds);

            // 공백 페이지 토큰을 마지막 페이지와 동일하게 처리한다.
            String responseNextPage = normalizeNextPage(response.nextPage());

            // 다음 페이지 토큰이 없으면 카드 목록 수집을 정상 종료한다.
            if (responseNextPage == null) {
                break;
            }

            // 이미 처리한 페이지 토큰이 다시 나타나면 무한 반복 전에 수집을 중단한다.
            if (!visitedNextPages.add(responseNextPage)) {
                throw new IllegalStateException("카드 목록 next_page가 반복되었습니다: " + responseNextPage);
            }

            // 다음 반복에서 직전 응답의 페이지 토큰을 그대로 Provider에 전달한다.
            nextPage = responseNextPage;
        }

        // 수집된 카드 순서를 유지하면서 외부에서 변경할 수 없는 목록으로 반환한다.
        return List.copyOf(consentedCardIds);
    }

    /**
     * 카드 한 장의 국내 승인내역을 마지막 페이지까지 조회해 정제 데이터로 변환
     *
     * @param userId 승인내역을 조회할 사용자 내부 ID
     * @param cardId 카드 목록 응답에서 얻은 동의 카드 고유 식별자
     * @param fromDate 모든 페이지에 동일하게 전달할 조회 시작일
     * @param toDate 모든 페이지에 동일하게 전달할 조회 종료일
     * @return 페이지와 응답 내부 순서를 유지한 카드 승인내역 목록
     */
    private List<CardApprovalData> collectCardApprovals(Long userId, String cardId, LocalDate fromDate,
                                                        LocalDate toDate) {
        List<CardApprovalData> approvals = new ArrayList<>();
        Set<String> visitedNextPages = new HashSet<>();
        String nextPage = null;

        while (true) {
            String requestNextPage = nextPage;

            // 동일 기간·페이지 조건의 외부 Provider 요청만 일시적 실패 시 한 번 재시도한다.
            String rawJson = externalCallRetryExecutor.execute(
                    "국내 승인내역 조회",
                    () -> myDataProvider.fetchApprovalDomesticRawJson(
                            userId,
                            cardId,
                            fromDate,
                            toDate,
                            requestNextPage,
                            PAGE_LIMIT
                    )
            );

            // 국내 승인내역 Raw JSON을 응답 DTO로 역직렬화한다.
            CardApprovalResponse response = deserialize(rawJson, CardApprovalResponse.class,
                    "국내 승인내역 응답(userId=" + userId + ", cardId=" + cardId + ", nextPage=" +
                            (requestNextPage == null ? "FIRST" : requestNextPage) + ")");

            // 내부 승인 데이터로 변환하기 전에 현재 페이지의 상태와 필드 규칙을 검증한다.
            cardApprovalValidator.validate(response);

            // 현재 페이지의 승인내역을 원본 순서대로 정제해 전체 카드 결과에 추가한다.
            approvals.addAll(cardApprovalParser.parseApprovals(cardId, response));

            // 공백 페이지 토큰을 마지막 페이지와 동일하게 처리한다.
            String responseNextPage = normalizeNextPage(response.nextPage());

            // 다음 페이지 토큰이 없으면 현재 카드의 승인내역 수집을 정상 종료한다.
            if (responseNextPage == null) {
                break;
            }

            // 이미 처리한 페이지 토큰이 다시 나타나면 무한 반복 전에 수집을 중단한다.
            if (!visitedNextPages.add(responseNextPage)) {
                throw new IllegalStateException("국내 승인내역 next_page가 반복되었습니다. cardId=" + cardId
                        + ", nextPage=" + responseNextPage);
            }

            // 다음 반복에서 직전 응답의 페이지 토큰을 그대로 Provider에 전달한다.
            nextPage = responseNextPage;
        }
        return approvals;
    }

    /**
     * Provider가 반환한 Raw JSON을 지정한 응답 DTO로 역직렬화
     *
     * @param rawJson Provider에서 받은 가공되지 않은 JSON 문자열
     * @param responseType 역직렬화할 응답 DTO 타입
     * @param errorContext 오류 메시지에 포함할 참가자·카드·페이지 문맥
     * @param <T> 응답 DTO 타입
     * @return 역직렬화가 완료된 응답 객체
     * @throws IllegalStateException Raw JSON이 없거나 Jackson 역직렬화에 실패한 경우
     */
    private <T> T deserialize(String rawJson, Class<T> responseType, String errorContext) {
        // Provider가 null 또는 빈 Raw JSON을 반환했는지 먼저 확인한다.
        if (rawJson == null || rawJson.isBlank()) {
            throw new IllegalStateException(errorContext + "이(가) 비어 있습니다.");
        }
        try {
            // API 필드명을 보존한 응답 DTO 타입으로 Raw JSON을 역직렬화한다.
            return objectMapper.readValue(rawJson, responseType);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException(
                    errorContext + "을(를) JSON으로 역직렬화할 수 없습니다.", exception);
        }
    }

    /**
     * 응답의 다음 페이지 토큰을 반복 처리에 사용할 값으로 정규화한다.
     *
     * @param nextPage 응답 DTO에 포함된 다음 페이지 기준개체
     * @return 미회신 또는 공백이면 {@code null}, 그 외에는 원본 토큰
     */
    private String normalizeNextPage(String nextPage) {
        // 페이지 토큰은 불투명한 값이므로 내용은 변경하지 않고 공백 여부만 판별한다.
        return nextPage == null || nextPage.isBlank() ? null : nextPage;
    }
}
