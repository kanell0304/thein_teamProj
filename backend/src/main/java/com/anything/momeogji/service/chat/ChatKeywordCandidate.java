package com.anything.momeogji.service.chat;

import java.util.List;

/** DB 사전과 음식점 테이블을 동일한 추출 입력으로 표현한다. */
public record ChatKeywordCandidate(
        Type type,
        String name,
        List<String> aliases
) {
    public ChatKeywordCandidate {
        if (type == null) {
            throw new IllegalArgumentException("키워드 유형은 필수입니다.");
        }
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("키워드 이름은 필수입니다.");
        }
        aliases = aliases == null
                ? List.of()
                : aliases.stream()
                        .filter(alias -> alias != null && !alias.isBlank())
                        .distinct()
                        .toList();
    }

    public enum Type {
        CATEGORY,
        MENU,
        RESTAURANT
    }
}
