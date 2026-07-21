package com.anything.momeogji.service.recommendation;

import com.anything.momeogji.dto.recommendation.RoundResponse;

public interface MeetupVoteService {

    /** 이미 투표한 후보에 다시 투표하면 아무 일도 하지 않는다(idempotent). */
    RoundResponse castVote(Long meetupId, Long roundId, Long roundCandidateId, Long callerId);

    /** 투표한 적 없는 후보를 취소해도 아무 일도 하지 않는다(idempotent). */
    RoundResponse retractVote(Long meetupId, Long roundId, Long roundCandidateId, Long callerId);
}
