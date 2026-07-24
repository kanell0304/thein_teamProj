package com.anything.momeogji.dto.chat;

/** API에서 확인할 수 있는 키워드별 긍정·부정 순점수. */
public record ChatMenuKeywordScoreResponse(
        String name,
        KeywordType type,
        int positiveCount,
        int negativeCount,
        int score
) {
    public enum KeywordType {
        MENU,
        CATEGORY,
        RESTAURANT
    }
}
