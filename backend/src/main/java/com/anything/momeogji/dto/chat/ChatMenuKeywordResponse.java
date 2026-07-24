package com.anything.momeogji.dto.chat;

import java.util.List;

/** 모먹지 기능 시작 직전 대화에서 집계한 전체 키워드 점수. */
public record ChatMenuKeywordResponse(
        List<ChatMenuKeywordScoreResponse> keywordScores
) {
    public ChatMenuKeywordResponse {
        keywordScores = List.copyOf(keywordScores);
    }
}
