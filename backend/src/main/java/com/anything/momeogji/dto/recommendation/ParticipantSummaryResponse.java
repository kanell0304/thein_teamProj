package com.anything.momeogji.dto.recommendation;

public record ParticipantSummaryResponse(
        Long participantId,
        Long memberId,
        String nickname,
        String submissionStatus,
        boolean isHost
) {
}
