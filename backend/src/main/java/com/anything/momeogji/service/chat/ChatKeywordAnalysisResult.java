package com.anything.momeogji.service.chat;

import java.util.List;

/** 대화에서 집계한 전체 키워드 점수. */
public record ChatKeywordAnalysisResult(
        List<ChatKeywordScore> keywordScores
) {
    public ChatKeywordAnalysisResult {
        keywordScores = List.copyOf(keywordScores);
    }

    public static ChatKeywordAnalysisResult empty() {
        return new ChatKeywordAnalysisResult(List.of());
    }
}
