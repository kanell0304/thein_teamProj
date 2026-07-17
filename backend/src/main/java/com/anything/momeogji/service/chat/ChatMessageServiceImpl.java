package com.anything.momeogji.service.chat;

import com.anything.momeogji.dto.chat.ChatMessageResponse;
import com.anything.momeogji.entity.ChatMessage;
import com.anything.momeogji.entity.ChatRoom;
import com.anything.momeogji.entity.Member;
import com.anything.momeogji.repository.ChatMessageRepository;
import com.anything.momeogji.repository.ChatRoomRepository;
import com.anything.momeogji.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ChatMessageServiceImpl implements ChatMessageService {

    private static final String BROADCAST_DESTINATION_PREFIX = "/topic/chatrooms/";

    private final ChatRoomRepository chatRoomRepository;
    private final MemberRepository memberRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final SimpMessagingTemplate messagingTemplate;

    @Override
    @Transactional
    public void saveAndBroadcast(Long chatRoomId, Long memberId, String content) {
        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("메시지 내용이 비어 있습니다.");
        }

        ChatRoom chatRoom = chatRoomRepository.findById(chatRoomId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 채팅방입니다: " + chatRoomId));
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 사용자입니다: " + memberId));

        ChatMessage saved = chatMessageRepository.save(ChatMessage.builder()
                .chatRoom(chatRoom)
                .user(member)
                .content(content)
                .createdAt(LocalDateTime.now())
                .build());

        messagingTemplate.convertAndSend(BROADCAST_DESTINATION_PREFIX + chatRoomId, toResponse(saved));
    }

    @Override
    public List<ChatMessageResponse> getRecentMessages(Long chatRoomId) {
        return chatMessageRepository.findTop50ByChatRoomIdOrderByCreatedAtDesc(chatRoomId).stream()
                .sorted(Comparator.comparing(ChatMessage::getCreatedAt))
                .map(this::toResponse)
                .toList();
    }

    private ChatMessageResponse toResponse(ChatMessage message) {
        Member user = message.getUser();
        return new ChatMessageResponse(
                message.getId(),
                message.getChatRoom().getId(),
                user != null ? user.getId() : null,
                user != null ? user.getNickname() : null,
                message.getContent(),
                message.getCreatedAt()
        );
    }
}
