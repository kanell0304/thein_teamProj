package com.anything.momeogji.controller.recommendation;

import com.anything.momeogji.dto.recommendation.MeetupCreateRequest;
import com.anything.momeogji.dto.recommendation.MeetupDetailResponse;
import com.anything.momeogji.dto.recommendation.MeetupResponse;
import com.anything.momeogji.service.recommendation.MeetupService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/meetups")
@RequiredArgsConstructor
public class MeetupController {

    private final MeetupService meetupService;

    @PostMapping
    public MeetupResponse createMeetup(@Valid @RequestBody MeetupCreateRequest request, Authentication authentication) {
        return meetupService.createMeetup(request.chatRoomId(), request.commonOption(), request.voteDeadlineAt(), memberId(authentication));
    }

    @GetMapping("/{meetupId}")
    public MeetupDetailResponse getMeetup(@PathVariable Long meetupId) {
        return meetupService.getMeetupDetail(meetupId);
    }

    // JwtAuthenticationFilter가 principal 자리에 memberId(Long)를 그대로 넣어둔다.
    private Long memberId(Authentication authentication) {
        return (Long) authentication.getPrincipal();
    }
}
