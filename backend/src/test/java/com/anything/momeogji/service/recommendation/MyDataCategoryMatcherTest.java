package com.anything.momeogji.service.recommendation;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MyDataCategoryMatcherTest {

    @Test
    void 카카오_세부_카테고리를_대분류로_매칭한다() {
        assertThat(MyDataCategoryMatcher.match("중식 > 중화요리")).isEqualTo("중식");
        assertThat(MyDataCategoryMatcher.match("일식 > 초밥")).isEqualTo("일식");
        assertThat(MyDataCategoryMatcher.match("카페 > 커피전문점")).isEqualTo("카페/디저트");
        assertThat(MyDataCategoryMatcher.match("고기 > 삼겹살")).isEqualTo("고기");
    }

    @Test
    void 매칭되지_않으면_null을_반환한다() {
        assertThat(MyDataCategoryMatcher.match("편의점 > 즉석식품")).isNull();
    }

    @Test
    void 비어있거나_null이면_null을_반환한다() {
        assertThat(MyDataCategoryMatcher.match(null)).isNull();
        assertThat(MyDataCategoryMatcher.match("")).isNull();
        assertThat(MyDataCategoryMatcher.match("   ")).isNull();
    }
}
