package com.anything.momeogji.controller.recommendation;

import com.anything.momeogji.dto.recommendation.FinalNoticeResponse;
import com.anything.momeogji.dto.recommendation.FinalNoticeUpdateRequest;
import com.anything.momeogji.service.recommendation.MeetupFinalizeService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Meetup Finalize [최종 확정]")
@RestController
@RequestMapping("/api/meetups/{meetupId}/final-notice")
@RequiredArgsConstructor
public class MeetupFinalizeController {

    private final MeetupFinalizeService meetupFinalizeService;

    @PostMapping
    public FinalNoticeResponse finalize(@PathVariable Long meetupId, Authentication authentication) {
        return meetupFinalizeService.finalize(meetupId, (Long) authentication.getPrincipal());
    }

    @PatchMapping
    public FinalNoticeResponse update(@PathVariable Long meetupId, @Valid @RequestBody FinalNoticeUpdateRequest request,
                                       Authentication authentication) {
        return meetupFinalizeService.updateFinalNotice(meetupId, request, (Long) authentication.getPrincipal());
    }
}
