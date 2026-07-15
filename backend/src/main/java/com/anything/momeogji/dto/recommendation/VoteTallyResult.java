package com.anything.momeogji.dto.recommendation;

import java.util.Map;

public record VoteTallyResult(String winnerRestaurantName, boolean tieBroken, Map<String, Long> voteCounts) {
}
