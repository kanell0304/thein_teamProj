package com.anything.momeogji.service.chat;

import com.anything.momeogji.dto.MemberDTO;
import com.anything.momeogji.dto.chat.ChatRoomResponse;
import com.anything.momeogji.entity.ChatRoom;
import com.anything.momeogji.entity.ChatRoomMember;
import com.anything.momeogji.entity.Member;
import com.anything.momeogji.repository.ChatRoomMemberRepository;
import com.anything.momeogji.repository.ChatRoomRepository;
import com.anything.momeogji.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ChatRoomServiceImpl implements ChatRoomService {

    private final ChatRoomRepository chatRoomRepository;
    private final ChatRoomMemberRepository chatRoomMemberRepository;
    private final MemberRepository memberRepository;

    @Override
    @Transactional
    public ChatRoomResponse createRoom(String name, Long hostMemberId) {
        Member host = findMember(hostMemberId);

        ChatRoom chatRoom = chatRoomRepository.save(ChatRoom.builder()
                .name(name)
                .build());

        chatRoomMemberRepository.save(ChatRoomMember.builder()
                .chatRoom(chatRoom)
                .user(host)
                .build());

        return new ChatRoomResponse(chatRoom.getId(), chatRoom.getName());
    }

    @Override
    @Transactional
    public void joinRoom(Long chatRoomId, Long memberId) {
        ChatRoom chatRoom = findChatRoom(chatRoomId);
        Member member = findMember(memberId);

        if (chatRoomMemberRepository.existsByChatRoomAndUser(chatRoom, member)) {
            return;
        }

        chatRoomMemberRepository.save(ChatRoomMember.builder()
                .chatRoom(chatRoom)
                .user(member)
                .build());
    }

    @Override
    @Transactional(readOnly = true)
    public List<MemberDTO> listMembers(Long chatRoomId) {
        return chatRoomMemberRepository.findByChatRoomId(chatRoomId).stream()
                .map(chatRoomMember -> {
                    Member member = chatRoomMember.getUser();
                    return new MemberDTO(member.getId(), member.getNickname(), member.getProfileImageUrl());
                })
                .toList();
    }

    private ChatRoom findChatRoom(Long chatRoomId) {
        return chatRoomRepository.findById(chatRoomId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 채팅방입니다: " + chatRoomId));
    }

    private Member findMember(Long memberId) {
        return memberRepository.findById(memberId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 사용자입니다: " + memberId));
    }
}
