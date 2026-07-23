package com.anything.momeogji.controller.chat;

import com.anything.momeogji.dto.chat.ChatMenuKeywordResponse;
import com.anything.momeogji.service.chat.ChatMenuKeywordService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

@RestController
@RequestMapping("/api/chatrooms/{chatRoomId}/menu-keywords")
@RequiredArgsConstructor
@Tag(name = "채팅방", description = "채팅방 생성·참여·목록 API")
@SecurityRequirement(name = "bearerAuth")
public class ChatMenuKeywordController {

    private final ChatMenuKeywordService chatMenuKeywordService;

    @GetMapping
    @Operation(summary = "최근 대화 메뉴 추출", description = "모먹지 기능 시작 직전 2시간의 사용자 대화에서 메뉴를 추출합니다.")
    public ChatMenuKeywordResponse getMenuKeywords(
            @PathVariable Long chatRoomId,
            @RequestParam
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            Instant featureStartedAt,
            Authentication authentication
    ) {
        return chatMenuKeywordService.extract(
                chatRoomId,
                (Long) authentication.getPrincipal(),
                featureStartedAt
        );
    }
}
