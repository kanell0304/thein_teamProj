package com.anything.momeogji.controller.chat;

import com.anything.momeogji.dto.chat.ChatMessageRequest;
import com.anything.momeogji.service.chat.ChatMessageService;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Controller;

import java.security.Principal;

// 실시간 채팅 메시지 수신(WebSocket/STOMP). 이력 조회는 ChatMessageRestController가 담당한다.
@Controller
@RequiredArgsConstructor
public class ChatMessageController {

    private final ChatMessageService chatMessageService;

    @MessageMapping("/chatrooms/{chatRoomId}/messages")
    public void sendMessage(@DestinationVariable Long chatRoomId, @Payload ChatMessageRequest request, Principal principal) {
        chatMessageService.saveAndBroadcast(chatRoomId, memberId(principal), request.content());
    }

    // StompAuthChannelInterceptor가 CONNECT 시점에 accessor.setUser(...)로 넣어둔 인증 정보.
    private Long memberId(Principal principal) {
        if (principal instanceof UsernamePasswordAuthenticationToken authentication) {
            return (Long) authentication.getPrincipal();
        }
        throw new IllegalStateException("인증 정보가 없습니다.");
    }
}
