package com.anything.momeogji.controller.recommendation;

import com.anything.momeogji.dto.recommendation.MeetupCreateRequest;
import com.anything.momeogji.dto.recommendation.MeetupDetailResponse;
import com.anything.momeogji.dto.recommendation.MeetupResponse;
import com.anything.momeogji.dto.recommendation.ParticipantSummaryResponse;
import com.anything.momeogji.service.recommendation.MeetupService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "Meetup [모임]")
@RestController
@RequestMapping("/api/meetups")
@RequiredArgsConstructor
public class MeetupController {

    private final MeetupService meetupService;

    @PostMapping
    public MeetupResponse createMeetup(@Valid @RequestBody MeetupCreateRequest request, Authentication authentication) {
        return meetupService.createMeetup(request.chatRoomId(), request.commonOption(), request.voteDeadlineAt(),
                request.participantIds(), memberId(authentication));
    }

    @GetMapping("/{meetupId}")
    public MeetupDetailResponse getMeetup(@PathVariable Long meetupId) {
        return meetupService.getMeetupDetail(meetupId);
    }

    // 재접속한 클라이언트가 놓친 웹소켓 이벤트를 복원할 때 쓴다. 진행 중인 모임이 없으면 204.
    @GetMapping("/active")
    public ResponseEntity<MeetupDetailResponse> getActiveMeetup(@RequestParam Long chatRoomId) {
        return meetupService.getActiveMeetupForChatRoom(chatRoomId)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @GetMapping("/{meetupId}/participants")
    public List<ParticipantSummaryResponse> listParticipants(@PathVariable Long meetupId) {
        return meetupService.listParticipants(meetupId);
    }

    // JwtAuthenticationFilter가 principal 자리에 memberId(Long)를 그대로 넣어둔다.
    private Long memberId(Authentication authentication) {
        return (Long) authentication.getPrincipal();
    }
}
