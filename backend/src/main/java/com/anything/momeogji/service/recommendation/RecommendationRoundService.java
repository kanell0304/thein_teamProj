package com.anything.momeogji.service.recommendation;

import com.anything.momeogji.dto.recommendation.PersonalOptionRequest;
import com.anything.momeogji.dto.recommendation.RoundCreateRequest;
import com.anything.momeogji.dto.recommendation.RoundResponse;
import com.anything.momeogji.entity.recommendation.Meetup;

import java.util.List;

public interface RecommendationRoundService {

    /**
     * 추천 회차를 만든다. 최초 추천/재추천 모두 이 메서드로 처리하며, 이전 회차 후보는 자동으로 제외 목록에 반영된다.
     * AI 호출 시작/완료/실패를 채팅방에 웹소켓으로 브로드캐스트한다.
     */
    RoundResponse createRound(Long meetupId, RoundCreateRequest request, Long callerId);

    /**
     * 참여자 선호 저장 없이 곧바로 AI 추천을 실행한다. 전원 개인 선호 제출 완료 시 자동 트리거되거나,
     * 호스트가 일부 응답만으로 강제 진행할 때 사용한다.
     */
    RoundResponse triggerAutoRecommendation(Meetup meetup, List<PersonalOptionRequest> personalOptions);
}
