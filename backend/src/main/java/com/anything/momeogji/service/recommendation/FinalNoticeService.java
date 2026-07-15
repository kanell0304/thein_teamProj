package com.anything.momeogji.service.recommendation;

import com.anything.momeogji.dto.recommendation.FinalNoticeResponse;
import com.anything.momeogji.dto.recommendation.RecommendationResult;
import com.anything.momeogji.dto.recommendation.VoteTallyResult;

import java.time.LocalDateTime;

public interface FinalNoticeService {

    FinalNoticeResponse buildFinalNotice(RecommendationResult recommendationResult,
                                          VoteTallyResult tallyResult,
                                          LocalDateTime meetingTime);
}
