package com.anything.momeogji.service.recommendation;

import com.anything.momeogji.dto.recommendation.RoundResponse;
import com.anything.momeogji.dto.recommendation.VoteSelectionRequest;

public interface MeetupVoteService {

    /** 이미 투표한 후보에 다시 투표하면 아무 일도 하지 않는다(idempotent). */
    RoundResponse castVote(Long meetupId, Long roundId, Long roundCandidateId, Long callerId);

    /** 투표한 적 없는 후보를 취소해도 아무 일도 하지 않는다(idempotent). */
    RoundResponse retractVote(Long meetupId, Long roundId, Long roundCandidateId, Long callerId);

    /** 현재 사용자의 복수 선택 전체를 한 번에 교체하고, 전원 제출 시 재추천/최종 결정을 처리한다. */
    RoundResponse replaceVotes(Long meetupId, Long roundId, VoteSelectionRequest request, Long callerId);

    /** 마감 시각이 지난 진행 중 투표를 현재 득표 결과로 정리한다. */
    void resolveExpiredVotes();
}
