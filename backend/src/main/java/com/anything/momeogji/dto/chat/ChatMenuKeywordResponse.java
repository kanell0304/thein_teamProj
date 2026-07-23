package com.anything.momeogji.dto.chat;

import java.util.List;

/** 모먹지 기능 시작 직전 대화에서 추출한 메뉴와 실제 분석한 사용자 메시지 수. */
public record ChatMenuKeywordResponse(
        List<String> menus,
        int analyzedMessageCount
) {
    public ChatMenuKeywordResponse {
        menus = List.copyOf(menus);
    }
}
