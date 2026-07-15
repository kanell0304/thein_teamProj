package com.anything.momeogji.service.recommendation;

import com.anything.momeogji.dto.recommendation.VoteRequest;
import com.anything.momeogji.dto.recommendation.VoteTallyResult;

import java.util.List;

public interface VotingService {

    /**
     * 중복 투표(여러 후보 동시 선택)를 허용해 집계하고, 공동 1등이 나오면 랜덤으로 최종 후보를 정한다.
     */
    VoteTallyResult tally(List<VoteRequest> votes);
}
