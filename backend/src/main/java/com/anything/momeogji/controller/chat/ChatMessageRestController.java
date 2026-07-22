package com.anything.momeogji.controller.chat;

import com.anything.momeogji.dto.chat.ChatMessageResponse;
import com.anything.momeogji.service.chat.ChatMessageService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 채팅방 입장 시 이력을 불러오는 REST. 실시간 신규 메시지는 WebSocket(ChatMessageController)이 담당한다. */
@Tag(name = "Chat Message [채팅 메시지]")
@RestController
@RequestMapping("/api/chatrooms/{chatRoomId}/messages")
@RequiredArgsConstructor
public class ChatMessageRestController {

    private final ChatMessageService chatMessageService;

    @GetMapping
    public List<ChatMessageResponse> getRecentMessages(@PathVariable Long chatRoomId) {
        return chatMessageService.getRecentMessages(chatRoomId);
    }
}
