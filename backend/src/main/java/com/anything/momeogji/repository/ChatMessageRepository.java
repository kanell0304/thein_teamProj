package com.anything.momeogji.repository;

import com.anything.momeogji.entity.ChatMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {

    List<ChatMessage> findTop50ByChatRoomIdOrderByCreatedAtDesc(Long chatRoomId);

    @Query("""
            select message
            from ChatMessage message
            where message.chatRoom.id = :chatRoomId
              and message.user is not null
              and message.user.id in :participantIds
              and message.createdAt >= :fromInclusive
              and message.createdAt < :toExclusive
            order by message.createdAt asc
            """)
    List<ChatMessage> findParticipantMessagesInPeriod(
            @Param("chatRoomId") Long chatRoomId,
            @Param("participantIds") List<Long> participantIds,
            @Param("fromInclusive") LocalDateTime fromInclusive,
            @Param("toExclusive") LocalDateTime toExclusive
    );

}
