package com.anything.momeogji.repository;

import com.anything.momeogji.entity.ChatRoom;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ChatRoomRepository extends JpaRepository<ChatRoom, Long> {

    Optional<ChatRoom> findByJoinCode(String joinCode);

    boolean existsByJoinCode(String joinCode);
}
