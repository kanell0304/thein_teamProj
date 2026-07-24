package com.anything.momeogji.service.chat;

/** 한 키워드의 긍정·부정 언급 횟수와 순점수. */
public record ChatKeywordScore(
        String name,
        ChatKeywordCandidate.Type type,
        int positiveCount,
        int negativeCount,
        int score
) {
}
