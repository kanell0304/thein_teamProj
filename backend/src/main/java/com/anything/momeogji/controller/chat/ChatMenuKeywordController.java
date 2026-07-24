package com.anything.momeogji.controller.chat;

import com.anything.momeogji.dto.chat.ChatMenuKeywordRequest;
import com.anything.momeogji.dto.chat.ChatMenuKeywordResponse;
import com.anything.momeogji.service.chat.ChatMenuKeywordService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/chatrooms/{chatRoomId}/menu-keywords")
@RequiredArgsConstructor
@Tag(name = "채팅방", description = "채팅방 생성·참여·목록 API")
@SecurityRequirement(name = "bearerAuth")
public class ChatMenuKeywordController {

    private final ChatMenuKeywordService chatMenuKeywordService;

    @PostMapping
    @Operation(summary = "최근 대화 메뉴 추출", description = "모먹지 참가자의 기능 시작 직전 2시간 대화에서 메뉴를 추출합니다.")
    public ChatMenuKeywordResponse getMenuKeywords(
            @PathVariable Long chatRoomId,
            @Valid @RequestBody ChatMenuKeywordRequest request,
            Authentication authentication
    ) {
        return chatMenuKeywordService.extract(
                chatRoomId,
                (Long) authentication.getPrincipal(),
                request.featureStartedAt(),
                request.participantIds()
        );
    }
}
