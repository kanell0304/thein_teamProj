package com.anything.momeogji.controller.friend;

import com.anything.momeogji.dto.MemberDTO;
import com.anything.momeogji.dto.friend.FriendRequestResponse;
import com.anything.momeogji.dto.friend.FriendRequestSendRequest;
import com.anything.momeogji.service.friend.FriendService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/friends")
@RequiredArgsConstructor
@Tag(name = "친구", description = "UID(회원 ID) 기반 친구 요청·수락·목록 API")
@SecurityRequirement(name = "bearerAuth")
public class FriendController {

    private final FriendService friendService;

    @Operation(summary = "친구 요청 보내기", description = "상대방의 UID(회원 ID)로 친구 요청을 보냅니다.")
    @PostMapping("/requests")
    public void sendRequest(@Valid @RequestBody FriendRequestSendRequest request, Authentication authentication) {
        friendService.sendRequest(memberId(authentication), request.targetUserId());
    }

    @Operation(summary = "받은 친구 요청 목록", description = "아직 수락/거절하지 않은 받은 요청을 조회합니다.")
    @GetMapping("/requests/received")
    public List<FriendRequestResponse> listReceivedRequests(Authentication authentication) {
        return friendService.listReceivedRequests(memberId(authentication));
    }

    @Operation(summary = "친구 요청 수락", description = "본인이 받은 요청만 수락할 수 있습니다.")
    @PostMapping("/requests/{requestId}/accept")
    public void accept(@PathVariable Long requestId, Authentication authentication) {
        friendService.accept(requestId, memberId(authentication));
    }

    @Operation(summary = "친구 요청 거절", description = "본인이 받은 요청만 거절할 수 있습니다.")
    @DeleteMapping("/requests/{requestId}")
    public void reject(@PathVariable Long requestId, Authentication authentication) {
        friendService.reject(requestId, memberId(authentication));
    }

    @Operation(summary = "내 친구 목록", description = "서로 수락이 완료된 친구만 조회합니다.")
    @GetMapping
    public List<MemberDTO> listFriends(Authentication authentication) {
        return friendService.listFriends(memberId(authentication));
    }

    // JwtAuthenticationFilter가 principal 자리에 memberId(Long)를 그대로 넣어둔다.
    private Long memberId(Authentication authentication) {
        return (Long) authentication.getPrincipal();
    }
}
