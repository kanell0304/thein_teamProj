package com.anything.momeogji.controller.dev;

import com.anything.momeogji.dto.chat.ChatMessageResponse;
import com.anything.momeogji.service.chat.DevChatSeedService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

/** dev 프로필에서만 제공되는 실제 채팅 시연 데이터 생성 API입니다. */
@RestController
@Profile("dev")
@RequestMapping("/api/dev/chatrooms")
@RequiredArgsConstructor
public class DevChatSeedController {

    private final DevChatSeedService devChatSeedService;

    @PostMapping("/{chatRoomId}/seed")
    public List<ChatMessageResponse> seedChat(
            @PathVariable Long chatRoomId,
            Authentication authentication
    ) {
        if (authentication == null || !(authentication.getPrincipal() instanceof Long memberId)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "로그인이 필요합니다.");
        }
        return devChatSeedService.seedWhenEmpty(chatRoomId, memberId);
    }
}
