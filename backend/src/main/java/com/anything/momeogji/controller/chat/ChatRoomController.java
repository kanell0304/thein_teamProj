package com.anything.momeogji.controller.chat;

import com.anything.momeogji.dto.chat.ChatRoomCreateRequest;
import com.anything.momeogji.dto.chat.ChatRoomResponse;
import com.anything.momeogji.dto.chat.ChatRoomListItemResponse;
import com.anything.momeogji.service.chat.ChatRoomService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.GetMapping;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

import java.util.List;

@RestController
@RequestMapping("/api/chatrooms")
@RequiredArgsConstructor
@Tag(name = "채팅방", description = "채팅방 생성·참여·목록 API")
@SecurityRequirement(name = "bearerAuth")
public class ChatRoomController {

    private final ChatRoomService chatRoomService;

    @Operation(summary = "내 채팅방 목록", description = "참가자 수와 최근 메시지를 포함해 최근 활동 순으로 조회합니다.")
    @GetMapping
    public List<ChatRoomListItemResponse> getMyRooms(Authentication authentication) {
        return chatRoomService.getMyRooms(memberId(authentication));
    }

    @Operation(summary = "채팅방 생성")
    @PostMapping
    public ChatRoomResponse createRoom(@Valid @RequestBody ChatRoomCreateRequest request, Authentication authentication) {
        return chatRoomService.createRoom(request.name(), memberId(authentication));
    }

    @Operation(summary = "채팅방 참여")
    @PostMapping("/{chatRoomId}/join")
    public void joinRoom(@PathVariable Long chatRoomId, Authentication authentication) {
        chatRoomService.joinRoom(chatRoomId, memberId(authentication));
    }

    // JwtAuthenticationFilter가 principal 자리에 memberId(Long)를 그대로 넣어둔다.
    private Long memberId(Authentication authentication) {
        return (Long) authentication.getPrincipal();
    }
}
