package com.anything.momeogji.controller.chat;

import com.anything.momeogji.dto.MemberDTO;
import com.anything.momeogji.dto.chat.ChatRoomCreateRequest;
import com.anything.momeogji.dto.chat.ChatRoomResponse;
import com.anything.momeogji.service.chat.ChatRoomService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.UnexpectedRollbackException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "Chat Room [채팅방]")
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
        try {
            chatRoomService.joinRoom(chatRoomId, memberId(authentication));
        } catch (UnexpectedRollbackException e) {
            // 동시에 들어온 두 참여 요청이 경합하면, 늦게 커밋을 시도한 트랜잭션은 unique 제약 위반으로
            // rollback-only 처리되어 이 예외로 나타난다 - 참여 자체는 먼저 요청이 이미 성공시켰으므로 무시한다.
        } catch (DataIntegrityViolationException e) {
            // ChatRoomMember는 IDENTITY 전략이라 save() 호출 즉시 insert가 실행되므로, 위와 같은 레이스가
            // 나면 flush 시점이 아니라 여기서 곧바로 unique 제약 위반이 터진다 - 마찬가지로 이미 다른 요청이
            // 참여를 성공시켰다는 뜻이므로 무시한다.
        }
    }

    @GetMapping("/{chatRoomId}/members")
    public List<MemberDTO> listMembers(@PathVariable Long chatRoomId) {
        return chatRoomService.listMembers(chatRoomId);
    }

    // JwtAuthenticationFilter가 principal 자리에 memberId(Long)를 그대로 넣어둔다.
    private Long memberId(Authentication authentication) {
        return (Long) authentication.getPrincipal();
    }
}
