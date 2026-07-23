package com.anything.momeogji.service.recommendation;

import java.util.List;
import java.util.Map;

/**
 * MyData의 카카오 세부 카테고리 문자열을, 참여자가 직접 고르는 선호 카테고리(CATEGORY_OPTIONS)와
 * 같은 대분류로 매칭한다. 정확한 카카오 카테고리 체계를 전부 알 수 없어, 자주 쓰이는 하위 키워드를
 * 기준으로 하는 최선 추정 매칭이다 — 매칭에 실패하면 그 방문 기록은 투표에서 조용히 제외된다.
 */
final class MyDataCategoryMatcher {

    private static final Map<String, List<String>> KEYWORDS_BY_CATEGORY = Map.ofEntries(
            Map.entry("한식", List.of("한식", "한정식", "국밥", "찌개,전골", "죽")),
            Map.entry("중식", List.of("중식", "중국음식")),
            Map.entry("일식", List.of("일식", "돈까스", "초밥", "라멘", "우동", "회,생선")),
            Map.entry("양식", List.of("양식", "이탈리안", "피자", "파스타", "스테이크")),
            Map.entry("분식", List.of("분식", "떡볶이", "김밥")),
            Map.entry("카페/디저트", List.of("카페", "디저트", "제과,베이커리", "아이스크림", "빙수")),
            Map.entry("고기", List.of("고기", "구이", "삼겹살", "갈비", "육류")),
            Map.entry("아시안", List.of("아시안", "아시아음식", "베트남", "태국", "인도"))
    );

    private MyDataCategoryMatcher() {
    }

    /** 매칭되는 대분류가 없으면 null을 반환한다. */
    static String match(String foodCategory) {
        if (foodCategory == null || foodCategory.isBlank()) {
            return null;
        }

        for (Map.Entry<String, List<String>> entry : KEYWORDS_BY_CATEGORY.entrySet()) {
            for (String keyword : entry.getValue()) {
                if (foodCategory.contains(keyword)) {
                    return entry.getKey();
                }
            }
        }
        return null;
    }
}
