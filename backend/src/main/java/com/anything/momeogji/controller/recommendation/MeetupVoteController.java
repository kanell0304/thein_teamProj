package com.anything.momeogji.controller.recommendation;

import com.anything.momeogji.dto.recommendation.RoundResponse;
import com.anything.momeogji.dto.recommendation.VoteSelectionRequest;
import com.anything.momeogji.service.recommendation.MeetupVoteService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.PutMapping;

@RestController
@RequestMapping("/api/meetups/{meetupId}/rounds/{roundId}")
@RequiredArgsConstructor
public class MeetupVoteController {

    private final MeetupVoteService meetupVoteService;

    @PostMapping("/candidates/{roundCandidateId}/votes")
    public RoundResponse castVote(@PathVariable Long meetupId, @PathVariable Long roundId, @PathVariable Long roundCandidateId,
                                   Authentication authentication) {
        return meetupVoteService.castVote(meetupId, roundId, roundCandidateId, (Long) authentication.getPrincipal());
    }

    @DeleteMapping("/candidates/{roundCandidateId}/votes")
    public RoundResponse retractVote(@PathVariable Long meetupId, @PathVariable Long roundId, @PathVariable Long roundCandidateId,
                                      Authentication authentication) {
        return meetupVoteService.retractVote(meetupId, roundId, roundCandidateId, (Long) authentication.getPrincipal());
    }

    /** 복수 선택을 원자적으로 교체해 마지막 참가자의 첫 요청에서 조기 확정되는 문제를 막는다. */
    @PutMapping("/votes")
    public RoundResponse replaceVotes(@PathVariable Long meetupId, @PathVariable Long roundId,
                                      @Valid @RequestBody VoteSelectionRequest request,
                                      Authentication authentication) {
        return meetupVoteService.replaceVotes(meetupId, roundId, request, (Long) authentication.getPrincipal());
    }
}
