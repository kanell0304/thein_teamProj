package com.anything.momeogji.service.chat;

import com.anything.momeogji.dto.auth.TokenResponse;
import com.anything.momeogji.dto.chat.ChatMessageResponse;
import com.anything.momeogji.repository.ChatMessageRepository;
import com.anything.momeogji.service.auth.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** 로컬 개발 환경의 빈 채팅방에 실제 DB·WebSocket 경로를 타는 예시 대화를 채웁니다. */
@Service
@Profile("dev")
@RequiredArgsConstructor
public class DevChatSeedService {

    private final AuthService authService;
    private final ChatRoomService chatRoomService;
    private final ChatMessageService chatMessageService;
    private final ChatMessageRepository chatMessageRepository;

    /** 메시지가 하나라도 있는 방은 그대로 두어 사용자가 만든 대화와 중복되지 않게 합니다. */
    @Transactional
    public List<ChatMessageResponse> seedWhenEmpty(Long chatRoomId, Long currentMemberId) {
        chatRoomService.joinRoom(chatRoomId, currentMemberId);
        if (chatMessageRepository.countByChatRoomId(chatRoomId) > 0) {
            return chatMessageService.getRecentMessages(chatRoomId);
        }

        TokenResponse seojun = authService.devLogin("dev-chat-seojun", "서준");
        TokenResponse gyeongjun = authService.devLogin("dev-chat-gyeongjun", "경준");
        chatRoomService.joinRoom(chatRoomId, seojun.memberId());
        chatRoomService.joinRoom(chatRoomId, gyeongjun.memberId());

        chatMessageService.saveAndBroadcast(chatRoomId, currentMemberId, "오늘 모먹지??");
        chatMessageService.saveAndBroadcast(chatRoomId, seojun.memberId(), "일식이나 초밥 어때요?");
        chatMessageService.saveAndBroadcast(chatRoomId, currentMemberId, "치킨은 오늘 말고 돈까스가 좋아요");
        chatMessageService.saveAndBroadcast(chatRoomId, gyeongjun.memberId(), "저도 초밥 좋아요. 모 먹지 써볼까요?");

        return chatMessageService.getRecentMessages(chatRoomId);
    }
}
