package com.anything.momeogji.dto.recommendation;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;

public record FinalizeRequest(
        @NotNull RecommendationResult recommendationResult,
        @NotNull VoteTallyResult tallyResult,
        @NotNull LocalDateTime meetingTime
) {
}
