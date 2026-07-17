package com.anything.momeogji.service.chat;

import com.anything.momeogji.dto.chat.ChatMessageResponse;

import java.util.List;

public interface ChatMessageService {

    /** 메시지를 저장하고, 그 방을 구독 중인 모두에게 실시간으로 브로드캐스트한다. */
    void saveAndBroadcast(Long chatRoomId, Long memberId, String content);

    /** 최근 메시지 50건을 오래된 순으로 반환한다(채팅방 입장 시 이력 로딩용). */
    List<ChatMessageResponse> getRecentMessages(Long chatRoomId);
}
