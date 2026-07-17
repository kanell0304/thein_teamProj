package com.anything.momeogji.service.chat;

import com.anything.momeogji.dto.chat.ChatRoomResponse;

public interface ChatRoomService {

    /** 채팅방을 만들고, 만든 사람을 바로 참여자로 등록한다. */
    ChatRoomResponse createRoom(String name, Long hostMemberId);

    /** 이미 참여 중이면 아무 일도 하지 않는다(중복 참여 방지). */
    void joinRoom(Long chatRoomId, Long memberId);
}
