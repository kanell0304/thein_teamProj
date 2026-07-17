package com.anything.momeogji.dto.chat;

import java.time.LocalDateTime;

/** 채팅 메시지 이력 조회(REST)와 실시간 브로드캐스트(WebSocket)에 공통으로 쓰는 응답 형태. */
public record ChatMessageResponse(
        Long id,
        Long chatRoomId,
        Long memberId,
        String nickname,
        String content,
        LocalDateTime createdAt
) {
}
