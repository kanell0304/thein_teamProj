package com.anything.momeogji.controller.chat;

import com.anything.momeogji.dto.MemberDTO;
import com.anything.momeogji.dto.chat.ChatRoomCreateRequest;
import com.anything.momeogji.dto.chat.ChatRoomInviteRequest;
import com.anything.momeogji.dto.chat.ChatRoomResponse;
import com.anything.momeogji.dto.chat.ChatRoomListItemResponse;
import com.anything.momeogji.service.chat.ChatRoomService;
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

    @Operation(summary = "채팅방 생성", description = "만든 사람과 선택된 참가자 전원을 바로 참여자로 등록합니다.")
    @PostMapping
    public ChatRoomResponse createRoom(@Valid @RequestBody ChatRoomCreateRequest request, Authentication authentication) {
        return chatRoomService.createRoom(request.name(), memberId(authentication), request.participantIds());
    }

    @Operation(summary = "채팅방 상세 조회")
    @GetMapping("/{chatRoomId}")
    public ChatRoomResponse getRoom(@PathVariable Long chatRoomId) {
        return chatRoomService.getRoom(chatRoomId);
    }

    @Operation(summary = "채팅방 참여")
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
    @Operation(summary = "채팅방 참가자 목록")
    public List<MemberDTO> listMembers(@PathVariable Long chatRoomId) {
        return chatRoomService.listMembers(chatRoomId);
    }

    @Operation(summary = "채팅방 참가자 초대", description = "이미 참여 중인 회원만 다른 회원을 초대할 수 있습니다.")
    @PostMapping("/{chatRoomId}/invite")
    public List<MemberDTO> inviteMembers(@PathVariable Long chatRoomId,
                                          @Valid @RequestBody ChatRoomInviteRequest request,
                                          Authentication authentication) {
        return chatRoomService.inviteMembers(chatRoomId, memberId(authentication), request.memberIds());
    }

    // JwtAuthenticationFilter가 principal 자리에 memberId(Long)를 그대로 넣어둔다.
    private Long memberId(Authentication authentication) {
        return (Long) authentication.getPrincipal();
    }
}
