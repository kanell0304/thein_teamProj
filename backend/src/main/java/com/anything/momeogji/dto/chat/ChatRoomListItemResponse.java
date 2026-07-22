package com.anything.momeogji.dto.chat;

import org.apache.ibatis.annotations.AutomapConstructor;

import java.time.LocalDateTime;

/** MyBatis 채팅방 목록 집계 결과와 프론트 목록 카드가 공유하는 응답. */
public record ChatRoomListItemResponse(
        Long id,
        String name,
        Long memberCount,
        String lastMessage,
        LocalDateTime lastMessageAt,
        Long unreadCount
) {
    @AutomapConstructor
    public ChatRoomListItemResponse {
    }
}
