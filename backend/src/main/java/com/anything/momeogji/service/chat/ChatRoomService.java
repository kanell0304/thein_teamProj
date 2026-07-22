package com.anything.momeogji.service.chat;

import com.anything.momeogji.dto.MemberDTO;
import com.anything.momeogji.dto.chat.ChatRoomResponse;

import java.util.List;

public interface ChatRoomService {

    // 채팅방을 만들고, 만든 사람을 바로 참여자로 등록(호스트)
    ChatRoomResponse createRoom(String name, Long hostMemberId);

    // 이미 참여 중이면 아무 일도 없음(중복 참여 방지).
    void joinRoom(Long chatRoomId, Long memberId);

    // 채팅방 참여자 목록을 조회 모임 참가자 선택 화면에서 사용
    List<MemberDTO> listMembers(Long chatRoomId);
}
