package com.anything.momeogji.mydata.collection.cardlist;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * 카드 목록 조회 v2의 Raw JSON 응답을 Java 타입으로 역직렬화하는 DTO
 *
 * 외부 API의 snake_case 필드명은 {@link JsonProperty}로 명시하고 Java 코드에서는 camelCase 접근자를 사용한다.
 * 이 DTO는 외부 응답 구조만 표현한다.
 *
 * @param responseCode 세부 응답코드
 * @param responseMessage 세부 응답메시지
 * @param searchTimestamp 조회 타임스탬프
 * @param nextPage 다음 페이지 기준개체. 마지막 페이지이면 {@code null}
 * @param cardCount 보유카드수
 * @param cards 보유카드목록
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record CardListResponse(
        @JsonProperty("rsp_code") String responseCode,
        @JsonProperty("rsp_msg") String responseMessage,
        @JsonProperty("search_timestamp") String searchTimestamp,
        @JsonProperty("next_page") String nextPage,
        @JsonProperty("card_cnt") Integer cardCount,
        @JsonProperty("card_list") List<CardItem> cards
) {

    /**
     * 카드 목록 응답에 포함된 카드 한 건의 Raw 구조
     *
     * 외부 API 값을 손실 없이 전달하기 위한 구조이므로 Enum으로 변환하거나 동의 여부에 따라 항목을 제외하지 않는다.
     *
     * @param cardId 카드 고유 식별자
     * @param maskedCardNumber 마스킹된 카드번호
     * @param transferConsented 해당 카드의 개인신용정보 전송요구 여부
     * @param cardName 카드상품명
     * @param cardMemberCode 본인·가족 구분 코드
     * @param cardTypeCode 카드 구분 코드
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record CardItem(
            @JsonProperty("card_id") String cardId,
            @JsonProperty("card_num") String maskedCardNumber,
            @JsonProperty("is_consent") Boolean transferConsented,
            @JsonProperty("card_name") String cardName,
            @JsonProperty("card_member") String cardMemberCode,
            @JsonProperty("card_type") String cardTypeCode
    ) {
    }
}
