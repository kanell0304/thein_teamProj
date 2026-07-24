package com.anything.momeogji.dto.chat;

import java.util.List;

/** 모먹지 기능 시작 직전 대화의 상위 메뉴, 전체 점수와 실제 분석한 사용자 메시지 수. */
public record ChatMenuKeywordResponse(
        List<String> menus,
        List<ChatMenuKeywordScoreResponse> keywordScores,
        int analyzedMessageCount
) {
    public ChatMenuKeywordResponse {
        menus = List.copyOf(menus);
        keywordScores = List.copyOf(keywordScores);
    }
}
