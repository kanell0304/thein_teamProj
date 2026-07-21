package com.anything.momeogji.service.recommendation;

import com.anything.momeogji.dto.recommendation.FinalNoticeResponse;
import com.anything.momeogji.dto.recommendation.MeetupInvitationEvent;
import com.anything.momeogji.dto.recommendation.MeetupProgressEvent;
import com.anything.momeogji.dto.recommendation.RecommendationProgressEvent;
import com.anything.momeogji.dto.recommendation.RecommendationProgressStatus;
import com.anything.momeogji.dto.recommendation.RoundResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

/** 추천/투표/최종공지 진행 상황을 채팅방 구독자에게 실시간으로 알린다. */
@Component
@RequiredArgsConstructor
public class RecommendationEventPublisher {

    private final SimpMessagingTemplate messagingTemplate;

    public void recommendationStarted(Long chatRoomId) {
        publishProgress(chatRoomId, new RecommendationProgressEvent(RecommendationProgressStatus.STARTED, null, null));
    }

    public void recommendationCompleted(Long chatRoomId, RoundResponse result) {
        publishProgress(chatRoomId, new RecommendationProgressEvent(RecommendationProgressStatus.COMPLETED, result, null));
    }

    public void recommendationFailed(Long chatRoomId, String errorMessage) {
        publishProgress(chatRoomId, new RecommendationProgressEvent(RecommendationProgressStatus.FAILED, null, errorMessage));
    }

    public void voteTallied(Long chatRoomId, RoundResponse result) {
        messagingTemplate.convertAndSend("/topic/chatrooms/" + chatRoomId + "/vote-updates", result);
    }

    public void finalNoticePublished(Long chatRoomId, FinalNoticeResponse response) {
        messagingTemplate.convertAndSend("/topic/chatrooms/" + chatRoomId + "/final-notice", response);
    }

    public void meetupInvitationSent(Long chatRoomId, MeetupInvitationEvent event) {
        messagingTemplate.convertAndSend("/topic/chatrooms/" + chatRoomId + "/meetup-invitations", event);
    }

    public void preferenceProgress(Long chatRoomId, MeetupProgressEvent event) {
        messagingTemplate.convertAndSend("/topic/chatrooms/" + chatRoomId + "/meetup-progress", event);
    }

    private void publishProgress(Long chatRoomId, RecommendationProgressEvent event) {
        messagingTemplate.convertAndSend("/topic/chatrooms/" + chatRoomId + "/recommendation-progress", event);
    }
}
