// 카드 목록과 카드별 국내 승인내역의 가공되지 않은 JSON 응답을 제공한다.

package com.anything.momeogji.mydata;

import java.time.LocalDate;

/**
 * MyDataService와 실제 데이터 제공 방식 사이의 경계를 정의한다.
 *
 * <p>현재 개발 단계에서는 {@code DummyMyDataProvider}가 classpath의 JSON 파일을
 * 반환하고, 실제 연동 단계에서는 같은 계약을 구현한 API Provider로 교체한다.
 * MyDataService는 구현체가 Dummy인지 실제 API인지 알 필요가 없다.</p>
 *
 * <p>이 Provider의 책임은 요청 조건에 대응하는 Raw JSON 문자열을 반환하는 것까지다.
 * 응답 필드 검증, JSON 역직렬화, 승인·취소·정정 처리와 옵션값 결합은 수행하지 않는다.</p>
 *
 * <p>{@code participantId}는 실제 금융 마이데이터 API의 인증 수단이 아니다.
 * 프로젝트에서는 참가자별 Dummy 파일을 찾기 위한 내부 라우팅 키로 사용하며,
 * 실제 API 구현에서는 접근 토큰과 기관코드가 별도의 인증 문맥에서 제공되어야 한다.</p>
 */
public interface MyDataProvider {

    /**
     * 참가자가 보유한 카드 목록의 한 페이지를 Raw JSON으로 조회한다.
     *
     * <p>실제 카드 목록 조회 v2의 페이지네이션 조건을 계약에 포함한다.
     * 최초 호출에서는 {@code searchTimestamp}에 {@code "0"}을 사용하고
     * {@code nextPage}를 비운다. 다음 페이지 호출에서는 직전 응답의
     * {@code next_page} 값을 그대로 전달한다.</p>
     *
     * @param participantId 참가자별 Dummy 응답 또는 인증 문맥을 찾기 위한 내부 ID
     * @param searchTimestamp 최근 조회 타임스탬프. 최초 호출은 {@code "0"}
     * @param nextPage 다음 페이지 기준개체. 최초 페이지이면 {@code null}
     * @param limit 페이지당 최대 조회 개수. 실제 API 기준 1 이상 500 이하
     * @return 가공하거나 파싱하지 않은 카드 목록 JSON. 정상 반환 시 {@code null}이 아니어야 한다.
     */
    String fetchCardList(
            Long participantId,
            String searchTimestamp,
            String nextPage,
            int limit
    );

    /**
     * 카드 한 장의 국내 승인내역 한 페이지를 Raw JSON으로 조회한다.
     *
     * <p>{@code cardId}는 카드 목록 응답에서 받은 {@code card_id}를 사용해야 한다.
     * 반복문의 순번이나 Dummy 파일 번호를 카드 식별자로 새로 만들면 안 된다.</p>
     *
     * <p>승인내역 조회 범위에는 승인뿐 아니라 같은 기간에 발생한 승인취소와 정정도
     * 포함된다. {@code nextPage}가 있으면 동일한 참가자·카드·기간 조건을 유지한 채
     * 다음 페이지를 요청해야 한다.</p>
     *
     * @param participantId 참가자별 Dummy 응답 또는 인증 문맥을 찾기 위한 내부 ID
     * @param cardId 카드 목록 조회 응답에서 얻은 카드 고유 식별자
     * @param fromDate 국내 승인내역 조회 시작일
     * @param toDate 국내 승인내역 조회 종료일
     * @param nextPage 다음 페이지 기준개체. 최초 페이지이면 {@code null}
     * @param limit 페이지당 최대 조회 개수. 실제 API 기준 1 이상 500 이하
     * @return 가공하거나 파싱하지 않은 국내 승인내역 JSON. 정상 반환 시 {@code null}이 아니어야 한다.
     */
    String fetchDomesticApprovals(
            Long participantId,
            String cardId,
            LocalDate fromDate,
            LocalDate toDate,
            String nextPage,
            int limit
    );
}
