package com.anything.momeogji.dto.chat;

/** 클라이언트가 STOMP /app/chatrooms/{chatRoomId}/messages 로 보내는 메시지 본문. */
public record ChatMessageRequest(String content) {
}
