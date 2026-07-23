package com.anything.momeogji.mydata.collection;

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
 * <p>{@code userId}는 실제 금융 마이데이터 API의 인증 수단이 아니다.
 * 프로젝트에서는 사용자별 Dummy 파일을 찾기 위한 내부 라우팅 키로 사용하며,
 * 실제 API 구현에서는 접근 토큰과 기관코드가 별도의 인증 문맥에서 제공되어야 한다.</p>
 *
 * <p>공개 메서드의 호출 조건과 반환값은 이 인터페이스에서 관리한다. 구현 클래스는
 * 같은 설명을 반복하지 않고 이 인터페이스의 Javadoc을 참조한다.</p>
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
     * <p>현재 {@code DummyMyDataProvider}는 처음 조회된 서로 다른 사용자 세 명을
     * 조회 순서대로 {@code classpath:mydata/dummy/user-01~03}에 대응시킨다.
     * 같은 사용자 ID는 기존 대응 관계를 재사용한다. Dummy 데이터가 단일 페이지이므로
     * {@code nextPage}와 {@code limit}은 파일 선택에 사용하지 않는다.</p>
     *
     * @param userId 사용자별 Dummy 응답 또는 실제 API 인증 문맥을 찾기 위한 내부 ID
     * @param searchTimestamp 최근 조회 타임스탬프. 최초 호출은 {@code "0"}; Dummy는 미회신 값과 14자리 숫자도 허용
     * @param nextPage 다음 페이지 기준개체. 최초 페이지이면 {@code null}; 현재 Dummy에서는 사용하지 않음
     * @param limit 페이지당 최대 조회 개수. 호출 계층에서 {@code 500}을 고정 전달하며 현재 Dummy에서는 사용하지 않음
     * @return 가공하거나 파싱하지 않은 UTF-8 카드 목록 JSON 문자열
     * @throws IllegalArgumentException 사용자 ID 또는 조회 타임스탬프 형식이 올바르지 않은 경우
     * @throws IllegalStateException Dummy 사용자 슬롯이 부족하거나 대응 파일을 읽을 수 없는 경우
     */
    String fetchCardListRawJson(Long userId, String searchTimestamp, String nextPage, int limit);

    /**
     * 카드 한 장의 국내 승인내역 한 페이지를 Raw JSON으로 조회한다.
     *
     * <p>{@code cardId}는 카드 목록 응답에서 받은 {@code card_id}를 사용해야 한다.
     * 반복문의 순번이나 Dummy 파일 번호를 카드 식별자로 새로 만들면 안 된다.</p>
     *
     * <p>호출 계층은 수집을 시작한 당일을 {@code toDate}로 정하고,
     * {@code toDate.minusYears(1)}을 {@code fromDate}로 전달하여 최근 1년 이력을
     * 조회해야 한다. 승인내역 조회 범위에는 승인뿐 아니라 같은 기간에 발생한
     * 승인취소와 정정도 포함된다.</p>
     *
     * <p>{@code nextPage}가 있으면 동일한 참가자·카드·기간 조건을 유지한 채
     * 다음 페이지를 요청해야 한다.</p>
     *
     * <p>현재 {@code DummyMyDataProvider}는 참가자 디렉터리에서
     * {@code approval-domestic-카드ID-page-NNN.json} 파일을 읽는다. 최초 요청의
     * {@code null} 페이지는 {@code page-001}에 대응하고, 후속 요청은 직전 응답의
     * {@code next_page}가 가리키는 파일을 선택한다. 조회 기간과 조회 개수는 정적
     * Dummy 파일 선택에 사용하지 않는다.</p>
     *
     * @param userId 사용자별 Dummy 응답 또는 실제 API 인증 문맥을 찾기 위한 내부 ID
     * @param cardId 카드 목록 조회 응답에서 얻은 카드 고유 식별자
     * @param fromDate 호출일 기준 1년 전인 국내 승인내역 조회 시작일; 현재 Dummy에서는 사용하지 않음
     * @param toDate 수집을 호출한 당일인 국내 승인내역 조회 종료일; 현재 Dummy에서는 사용하지 않음
     * @param nextPage 다음 페이지 기준개체. 최초 페이지이면 {@code null}; Dummy 후속 페이지는 {@code page-NNN} 형식
     * @param limit 페이지당 최대 조회 개수. 호출 계층에서 {@code 500}을 고정 전달하며 현재 Dummy에서는 사용하지 않음
     * @return 가공하거나 파싱하지 않은 UTF-8 국내 승인내역 JSON 문자열
     * @throws IllegalArgumentException 사용자 ID 또는 카드 ID 형식이 올바르지 않은 경우
     * @throws IllegalStateException Dummy 사용자 슬롯이 부족하거나 대응 파일을 읽을 수 없는 경우
     */
    String fetchApprovalDomesticRawJson(Long userId, String cardId, LocalDate fromDate,
            LocalDate toDate, String nextPage, int limit);
}
