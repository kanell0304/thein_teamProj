package com.anything.momeogji.repository;

import com.anything.momeogji.entity.ChatMessage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {

    List<ChatMessage> findTop50ByChatRoomIdOrderByCreatedAtDesc(Long chatRoomId);

    /** 개발용 기본 대화가 이미 들어 있는지 확인해 중복 생성을 막습니다. */
    long countByChatRoomId(Long chatRoomId);
}
