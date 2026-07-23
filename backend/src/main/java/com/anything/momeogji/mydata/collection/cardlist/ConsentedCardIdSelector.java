package com.anything.momeogji.mydata.collection.cardlist;

import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 검증된 카드 목록 응답에서 전송에 동의한 카드 ID만 추출
 *
 * 이 클래스는 전송에 동의한 카드만 원래 순서대로 선별하며 카드 상세정보를 별도 모델로 변환하지 않는다.
 */
@Component
public class ConsentedCardIdSelector {

    /**
     * 전송요구에 동의한 카드 ID를 원본 카드 목록 순서대로 반환한다.
     *
     * <p>호출자는 이 메서드보다 먼저 {@link CardListValidator#validate(CardListResponse)}를 호출해
     * 응답과 카드 목록의 유효성을 보장해야 한다. 동의한 카드가 없으면 예외 대신 빈 목록을 반환한다.
     * {@link java.util.stream.Stream#toList()} 결과를 사용하므로 반환된 목록은 변경할 수 없다.</p>
     *
     * @param response {@link CardListValidator} 검증을 통과한 카드 목록 응답
     * @return 동의한 카드 ID의 불변 목록
     */
    public List<String> selectCardIds(CardListResponse response) {
        return response.cards().stream()
                .filter(card -> Boolean.TRUE.equals(card.transferConsented()))
                .map(CardListResponse.CardItem::cardId)
                .toList();
    }
}
