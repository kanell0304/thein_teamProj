package com.anything.momeogji.repository;

import com.anything.momeogji.entity.ChatRoom;
import com.anything.momeogji.entity.ChatRoomMember;
import com.anything.momeogji.entity.Member;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ChatRoomMemberRepository extends JpaRepository<ChatRoomMember, Long> {

    boolean existsByChatRoomAndUser(ChatRoom chatRoom, Member user);

    boolean existsByChatRoomIdAndUserId(Long chatRoomId, Long userId);

    List<ChatRoomMember> findByChatRoomId(Long chatRoomId);
}
