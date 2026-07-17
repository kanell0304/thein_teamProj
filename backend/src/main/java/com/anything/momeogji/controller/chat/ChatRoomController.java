package com.anything.momeogji.controller.chat;

import com.anything.momeogji.dto.chat.ChatRoomCreateRequest;
import com.anything.momeogji.dto.chat.ChatRoomResponse;
import com.anything.momeogji.service.chat.ChatRoomService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/chatrooms")
@RequiredArgsConstructor
public class ChatRoomController {

    private final ChatRoomService chatRoomService;

    @PostMapping
    public ChatRoomResponse createRoom(@Valid @RequestBody ChatRoomCreateRequest request, Authentication authentication) {
        return chatRoomService.createRoom(request.name(), memberId(authentication));
    }

    @PostMapping("/{chatRoomId}/join")
    public void joinRoom(@PathVariable Long chatRoomId, Authentication authentication) {
        chatRoomService.joinRoom(chatRoomId, memberId(authentication));
    }

    // JwtAuthenticationFilter가 principal 자리에 memberId(Long)를 그대로 넣어둔다.
    private Long memberId(Authentication authentication) {
        return (Long) authentication.getPrincipal();
    }
}
