package com.anything.momeogji.mydata.cardlist;

import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 카드 목록 Raw 응답을 내부 처리에 사용하기 전에 응답 상태와 필드 규칙을 검증
 *
 * {@link CardListResponse} 데이터가 카드 목록 API 계약을 만족하는지 확인한다.
 * 검증을 통과한 응답은 {@link CardListParser}가 사용할 수 있다.
 */
@Component
public class CardListValidator {

    private static final String SUCCESS_RESPONSE_CODE = "00000";
    private static final Set<String> MEMBER_CODES = Set.of("1", "2");
    private static final Set<String> CARD_TYPE_CODES = Set.of("01", "02", "03");

    /**
     * 카드 목록 응답의 공통 필드와 카드별 필수값을 검증
     *
     * @param response ObjectMapper로 역직렬화한 카드 목록 응답
     * @throws IllegalArgumentException 응답 코드 또는 필드 규칙이 올바르지 않은 경우
     */
    public void validate(CardListResponse response) {
        // ObjectMapper 역직렬화 결과인 카드 목록 응답 객체의 누락 여부를 검사한다.
        if (response == null) {
            throw new IllegalArgumentException("카드 목록 응답은 null일 수 없습니다.");
        }

        // 후속 파싱이 가능한 정상 응답 코드인 00000인지 검사한다.
        if (!SUCCESS_RESPONSE_CODE.equals(response.responseCode())) {
            throw new IllegalArgumentException(
                    "카드 목록 조회가 실패했습니다. rsp_code=" + response.responseCode()
            );
        }

        // 세부 응답메시지 rsp_msg가 비어 있지 않은지 검사한다.
        validText(response.responseMessage(), "rsp_msg");

        // 응답의 조회 타임스탬프가 미회신 값, 0 또는 14자리 숫자인지 검사한다.
        validateSearchTimestamp(response.searchTimestamp());

        // 보유카드수 card_cnt가 누락되지 않았고 0 이상의 정수인지 검사한다.
        Integer cardCount = response.cardCount();
        if (cardCount == null || cardCount < 0) {
            throw new IllegalArgumentException("card_cnt는 0 이상의 정수여야 합니다.");
        }

        // 보유카드목록 card_list 자체가 누락되지 않았는지 검사한다.
        List<CardListResponse.CardItem> cards = response.cards();
        if (cards == null) {
            throw new IllegalArgumentException("card_list는 null일 수 없습니다.");
        }

        // 응답에 선언된 card_cnt와 실제 card_list 항목 수가 일치하는지 검사한다.
        if (cardCount != cards.size()) {
            throw new IllegalArgumentException(
                    "card_cnt와 card_list 크기가 일치하지 않습니다. card_cnt="
                            + cardCount + ", 실제 크기=" + cards.size()
            );
        }

        // 모든 카드 항목을 순서대로 검사하면서 카드 ID 중복 여부를 함께 확인한다.
        Set<String> cardIds = new HashSet<>();
        for (int index = 0; index < cards.size(); index++) {
            validCard(cards.get(index), index, cardIds);
        }
    }

    /**
     * 카드 목록의 단일 카드 항목에 필요한 필드와 코드값, 카드 ID 중복 여부를 검증
     *
     * @param card 검사할 카드 항목
     * @param index 오류 메시지에 표시할 카드 목록 위치
     * @param cardIds 앞에서 확인한 카드 ID를 저장하여 중복을 검사하는 집합
     * @throws IllegalArgumentException 카드가 없거나 필수값·코드값·카드 ID가 올바르지 않은 경우
     */
    private void validCard(
            CardListResponse.CardItem card,
            int index,
            Set<String> cardIds
    ) {
        // card_list 안의 개별 카드 항목이 null인지 검사한다.
        if (card == null) {
            throw new IllegalArgumentException("card_list[" + index + "]는 null일 수 없습니다.");
        }

        // 카드 고유 식별자 card_id가 비어 있지 않은지 검사한다.
        validText(card.cardId(), "card_list[" + index + "].card_id");

        // 마스킹된 카드번호 card_num이 비어 있지 않은지 검사한다.
        validText(card.maskedCardNumber(), "card_list[" + index + "].card_num");

        // 카드상품명 card_name이 비어 있지 않은지 검사한다.
        validText(card.cardName(), "card_list[" + index + "].card_name");

        // 본인·가족 구분 코드 card_member가 비어 있지 않은지 검사한다.
        validText(card.memberCode(), "card_list[" + index + "].card_member");

        // 카드 구분 코드 card_type이 비어 있지 않은지 검사한다.
        validText(card.typeCode(), "card_list[" + index + "].card_type");

        // 전송요구 여부 is_consent가 true 또는 false로 명시됐는지 검사한다.
        if (card.consented() == null) {
            throw new IllegalArgumentException(
                    "card_list[" + index + "].is_consent는 null일 수 없습니다."
            );
        }

        // 같은 카드 목록 응답 안에 동일한 card_id가 중복됐는지 검사한다.
        if (!cardIds.add(card.cardId())) {
            throw new IllegalArgumentException("중복된 card_id입니다: " + card.cardId());
        }

        // card_member가 본인 1 또는 가족 2 코드인지 검사한다.
        if (!MEMBER_CODES.contains(card.memberCode())) {
            throw new IllegalArgumentException(
                    "card_member는 1 또는 2여야 합니다: " + card.memberCode()
            );
        }

        // card_type이 신용 01, 체크 02, 소액신용체크 03 코드인지 검사한다.
        if (!CARD_TYPE_CODES.contains(card.typeCode())) {
            throw new IllegalArgumentException(
                    "card_type은 01, 02, 03 중 하나여야 합니다: " + card.typeCode()
            );
        }
    }

    /**
     * 조회 타임스탬프가 미회신 값, 최초 조회값 {@code 0}, 또는 14자리 숫자 형식인지 검증한다.
     *
     * @param searchTimestamp 검사할 카드 목록 조회 타임스탬프
     * @throws IllegalArgumentException 허용된 타임스탬프 형식이 아닌 경우
     */
    private void validateSearchTimestamp(String searchTimestamp) {
        // Timestamp 로직 미지원에 따른 미회신 값과 최초 조회값 0을 정상값으로 허용한다.
        if (searchTimestamp == null || searchTimestamp.isBlank() || "0".equals(searchTimestamp)) {
            return;
        }

        // 실제 조회 타임스탬프가 YYYYMMDDhhmmss 길이의 14자리 숫자인지 검사한다.
        if (!searchTimestamp.matches("\\d{14}")) {
            throw new IllegalArgumentException(
                    "search_timestamp는 0 또는 14자리 숫자여야 합니다: " + searchTimestamp
            );
        }
    }

    /**
     * 문자열 필드가 {@code null}, 빈 문자열 또는 공백 문자열이 아닌지 확인한다.
     *
     * @param value 검사할 문자열 값
     * @param fieldName 오류 메시지에 표시할 외부 API 필드명
     * @throws IllegalArgumentException 값이 없거나 공백으로만 구성된 경우
     */
    private void validText(String value, String fieldName) {
        // API 명세상 필수 문자열이 null, 빈 문자열 또는 공백 문자열인지 검사한다.
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + "은(는) 필수입니다.");
        }
    }
}
