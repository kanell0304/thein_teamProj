package com.anything.momeogji.service.chat;

import com.anything.momeogji.dto.MemberDTO;
import com.anything.momeogji.dto.chat.ChatRoomResponse;
import com.anything.momeogji.dto.chat.ChatRoomListItemResponse;
import com.anything.momeogji.entity.ChatRoom;
import com.anything.momeogji.entity.ChatRoomMember;
import com.anything.momeogji.entity.Member;
import com.anything.momeogji.repository.ChatRoomMemberRepository;
import com.anything.momeogji.repository.ChatRoomRepository;
import com.anything.momeogji.repository.MemberRepository;
import com.anything.momeogji.mapper.chat.ChatRoomQueryMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class ChatRoomServiceImpl implements ChatRoomService {

    private final ChatRoomRepository chatRoomRepository;
    private final ChatRoomMemberRepository chatRoomMemberRepository;
    private final MemberRepository memberRepository;
    private final ChatRoomQueryMapper chatRoomQueryMapper;

    @Override
    @Transactional
    public ChatRoomResponse createRoom(String name, Long hostMemberId, List<Long> participantIds) {
        // 참가자가 실존하는지 먼저 전부 확인한 뒤에 방을 만든다(일부만 저장되는 상태 방지).
        Set<Long> memberIds = new LinkedHashSet<>(participantIds);
        memberIds.add(hostMemberId);
        List<Member> members = memberIds.stream().map(this::findMember).toList();

        ChatRoom chatRoom = chatRoomRepository.save(ChatRoom.builder()
                .name(name)
                .build());

        for (Member member : members) {
            chatRoomMemberRepository.save(ChatRoomMember.builder()
                    .chatRoom(chatRoom)
                    .user(member)
                    .build());
        }

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
    public List<ChatRoomListItemResponse> getMyRooms(Long memberId) {
        findMember(memberId);
        return chatRoomQueryMapper.findAllByMemberId(memberId);
    }

    @Override
    @Transactional(readOnly = true)
    public ChatRoomResponse getRoom(Long chatRoomId) {
        ChatRoom chatRoom = findChatRoom(chatRoomId);
        return new ChatRoomResponse(chatRoom.getId(), chatRoom.getName());
    }

    @Override
    @Transactional(readOnly = true)
    public List<MemberDTO> listMembers(Long chatRoomId) {
        findChatRoom(chatRoomId);
        return toMemberDtos(chatRoomId);
    }

    @Override
    @Transactional
    public List<MemberDTO> inviteMembers(Long chatRoomId, Long inviterId, List<Long> memberIds) {
        ChatRoom chatRoom = findChatRoom(chatRoomId);
        if (!chatRoomMemberRepository.existsByChatRoomIdAndUserId(chatRoomId, inviterId)) {
            throw new IllegalArgumentException("채팅방 참여자만 다른 사람을 초대할 수 있습니다.");
        }

        for (Long memberId : new LinkedHashSet<>(memberIds)) {
            Member member = findMember(memberId);
            if (!chatRoomMemberRepository.existsByChatRoomAndUser(chatRoom, member)) {
                chatRoomMemberRepository.save(ChatRoomMember.builder()
                        .chatRoom(chatRoom)
                        .user(member)
                        .build());
            }
        }

        return toMemberDtos(chatRoomId);
    }

    private List<MemberDTO> toMemberDtos(Long chatRoomId) {
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
