package com.anything.momeogji.controller.recommendation;

import com.anything.momeogji.dto.recommendation.PreferenceSubmitRequest;
import com.anything.momeogji.dto.recommendation.PreferenceSubmitResponse;
import com.anything.momeogji.dto.recommendation.RoundResponse;
import com.anything.momeogji.service.recommendation.MeetupPreferenceService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Meetup Preference [개인 선호]")
@RestController
@RequestMapping("/api/meetups/{meetupId}/preferences")
@RequiredArgsConstructor
public class MeetupPreferenceController {

    private final MeetupPreferenceService meetupPreferenceService;

    @PostMapping("/me")
    public PreferenceSubmitResponse submitMyPreference(@PathVariable Long meetupId,
                                                         @Valid @RequestBody PreferenceSubmitRequest request,
                                                         Authentication authentication) {
        return meetupPreferenceService.submitPreference(meetupId, memberId(authentication), request);
    }

    @PostMapping("/force-start")
    public RoundResponse forceStartRecommendation(@PathVariable Long meetupId, Authentication authentication) {
        return meetupPreferenceService.forceStartRecommendation(meetupId, memberId(authentication));
    }

    // JwtAuthenticationFilter가 principal 자리에 memberId(Long)를 그대로 넣어둔다.
    private Long memberId(Authentication authentication) {
        return (Long) authentication.getPrincipal();
    }
}
