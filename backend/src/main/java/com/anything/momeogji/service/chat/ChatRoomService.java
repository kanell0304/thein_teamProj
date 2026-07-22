package com.anything.momeogji.service.chat;

import com.anything.momeogji.dto.MemberDTO;
import com.anything.momeogji.dto.chat.ChatRoomResponse;
import com.anything.momeogji.dto.chat.ChatRoomListItemResponse;

import java.util.List;

public interface ChatRoomService {

    /** 채팅방을 만들고, 만든 사람과 선택된 참가자 전원을 바로 참여자로 등록한다. */
    ChatRoomResponse createRoom(String name, Long hostMemberId, List<Long> participantIds);

    /** 이미 참여 중이면 아무 일도 하지 않는다(중복 참여 방지). */
    void joinRoom(Long chatRoomId, Long memberId);

    /** 로그인 회원이 참여한 채팅방을 최근 메시지 순으로 조회한다. */
    List<ChatRoomListItemResponse> getMyRooms(Long memberId);

    /** 채팅방 헤더에 표시할 이름 등 단건 정보를 조회한다. */
    ChatRoomResponse getRoom(Long chatRoomId);

    /** 채팅방 참가자 선택 화면에 사용할 회원 목록을 조회한다. */
    List<MemberDTO> listMembers(Long chatRoomId);

    /** 이미 참여 중인 회원이 다른 회원들을 채팅방에 초대한다. 이미 참여 중인 대상은 건너뛴다. */
    List<MemberDTO> inviteMembers(Long chatRoomId, Long inviterId, List<Long> memberIds);
}
