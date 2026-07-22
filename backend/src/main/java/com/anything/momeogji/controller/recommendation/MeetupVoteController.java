package com.anything.momeogji.controller.recommendation;

import com.anything.momeogji.dto.recommendation.RoundResponse;
import com.anything.momeogji.service.recommendation.MeetupVoteService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Meetup Vote [투표]")
@RestController
@RequestMapping("/api/meetups/{meetupId}/rounds/{roundId}/candidates/{roundCandidateId}/votes")
@RequiredArgsConstructor
public class MeetupVoteController {

    private final MeetupVoteService meetupVoteService;

    @PostMapping
    public RoundResponse castVote(@PathVariable Long meetupId, @PathVariable Long roundId, @PathVariable Long roundCandidateId,
                                   Authentication authentication) {
        return meetupVoteService.castVote(meetupId, roundId, roundCandidateId, (Long) authentication.getPrincipal());
    }

    @DeleteMapping
    public RoundResponse retractVote(@PathVariable Long meetupId, @PathVariable Long roundId, @PathVariable Long roundCandidateId,
                                      Authentication authentication) {
        return meetupVoteService.retractVote(meetupId, roundId, roundCandidateId, (Long) authentication.getPrincipal());
    }
}
