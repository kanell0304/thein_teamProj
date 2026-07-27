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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.UnexpectedRollbackException;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class ChatRoomServiceImpl implements ChatRoomService {

    // 혼동되는 문자(0/O, 1/I)를 뺀 알파벳으로 8자리 참여 코드를 만든다.
    private static final String JOIN_CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final int JOIN_CODE_LENGTH = 8;
    private static final int JOIN_CODE_MAX_ATTEMPTS = 5;
    private static final SecureRandom RANDOM = new SecureRandom();

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
                .joinCode(generateUniqueJoinCode())
                .build());

        for (Member member : members) {
            chatRoomMemberRepository.save(ChatRoomMember.builder()
                    .chatRoom(chatRoom)
                    .user(member)
                    .build());
        }

        return new ChatRoomResponse(chatRoom.getId(), chatRoom.getName(), chatRoom.getJoinCode());
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
    @Transactional
    public ChatRoomResponse joinRoomByCode(String joinCode, Long memberId) {
        ChatRoom chatRoom = chatRoomRepository.findByJoinCode(joinCode)
                .orElseThrow(() -> new IllegalArgumentException("유효하지 않은 참여 코드입니다."));

        try {
            joinRoom(chatRoom.getId(), memberId);
        } catch (UnexpectedRollbackException | DataIntegrityViolationException e) {
            // 동시에 같은 코드로 참여 요청이 경합하면 늦은 쪽이 unique 제약 위반을 겪는데,
            // 이미 다른 요청이 참여를 성공시켰다는 뜻이므로 무시한다(joinRoom의 기존 관례와 동일).
        }

        return new ChatRoomResponse(chatRoom.getId(), chatRoom.getName(), chatRoom.getJoinCode());
    }

    // ===== 충돌 시 재시도하는 참여 코드 발급 =====
    private String generateUniqueJoinCode() {
        for (int attempt = 0; attempt < JOIN_CODE_MAX_ATTEMPTS; attempt++) {
            String candidate = generateJoinCode();
            if (!chatRoomRepository.existsByJoinCode(candidate)) {
                return candidate;
            }
        }
        throw new IllegalStateException("참여 코드 발급에 반복적으로 실패했습니다.");
    }

    private String generateJoinCode() {
        StringBuilder builder = new StringBuilder(JOIN_CODE_LENGTH);
        for (int i = 0; i < JOIN_CODE_LENGTH; i++) {
            builder.append(JOIN_CODE_ALPHABET.charAt(RANDOM.nextInt(JOIN_CODE_ALPHABET.length())));
        }
        return builder.toString();
    }

    @Override
    @Transactional(readOnly = true)
    public List<ChatRoomListItemResponse> getMyRooms(Long memberId) {
        findMember(memberId);
        return chatRoomQueryMapper.findAllByMemberId(memberId);
    }

    @Override
    @Transactional
    public ChatRoomResponse getRoom(Long chatRoomId) {
        ChatRoom chatRoom = findChatRoom(chatRoomId);
        ensureJoinCode(chatRoom);
        return new ChatRoomResponse(chatRoom.getId(), chatRoom.getName(), chatRoom.getJoinCode());
    }

    // ===== 참여 코드 도입 이전에 만들어진 채팅방에 조회 시점 코드를 채워 넣는다 =====
    private void ensureJoinCode(ChatRoom chatRoom) {
        if (chatRoom.getJoinCode() != null) {
            return;
        }
        chatRoom.assignJoinCode(generateUniqueJoinCode());
        chatRoomRepository.save(chatRoom);
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
