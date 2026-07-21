package com.anything.momeogji.service.recommendation;

import com.anything.momeogji.dto.recommendation.RoundCreateRequest;
import com.anything.momeogji.dto.recommendation.RoundResponse;

public interface RecommendationRoundService {

    /**
     * 추천 회차를 만든다. 최초 추천/재추천 모두 이 메서드로 처리하며, 이전 회차 후보는 자동으로 제외 목록에 반영된다.
     * AI 호출 시작/완료/실패를 채팅방에 웹소켓으로 브로드캐스트한다.
     */
    RoundResponse createRound(Long meetupId, RoundCreateRequest request, Long callerId);
}
