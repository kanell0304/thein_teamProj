package com.anything.momeogji.dto.recommendation;

import java.util.List;

public record CandidateSummary(
        Long roundCandidateId,
        int rank,
        String name,
        String category,
        String roadAddress,
        String address,
        Double latitude,
        Double longitude,
        String reason,
        String imageUrl,
        long voteCount,
        String candidateType,
        List<Long> voterIds
) {
}
