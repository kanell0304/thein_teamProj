package com.anything.momeogji.service.chat;

import java.util.List;

/** 화면에 노출할 상위 메뉴와 디버깅용 전체 키워드 점수. */
public record ChatKeywordAnalysisResult(
        List<String> menus,
        List<ChatKeywordScore> keywordScores
) {
    public ChatKeywordAnalysisResult {
        menus = List.copyOf(menus);
        keywordScores = List.copyOf(keywordScores);
    }

    public static ChatKeywordAnalysisResult empty() {
        return new ChatKeywordAnalysisResult(List.of(), List.of());
    }
}
