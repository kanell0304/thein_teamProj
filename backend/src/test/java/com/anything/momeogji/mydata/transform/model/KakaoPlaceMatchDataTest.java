package com.anything.momeogji.mydata.transform.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link KakaoPlaceMatchData}가 AI 전달 전 단계에 필요한 장소명·전체 카테고리 경로 계약을 강제하는지 검증한다.
 */
class KakaoPlaceMatchDataTest {

    /**
     * 가맹점 집계 직후 사용하는 분류 전 객체가 장소 정보를 포함하지 않는지 확인한다.
     */
    @Test
    void 분류전_기본값은_장소명과_카테고리가_비어있다() {
        KakaoPlaceMatchData result = KakaoPlaceMatchData.unclassified();

        assertThat(result.placeName()).isNull();
        assertThat(result.categoryName()).isNull();
    }

    /**
     * 카카오에서 확정한 장소명과 전체 카테고리 경로를 원본 그대로 보존하는지 확인한다.
     */
    @Test
    void 매칭된_장소명과_전체_카테고리_경로를_보존한다() {
        KakaoPlaceMatchData result = new KakaoPlaceMatchData(
                "영인성",
                "음식점 > 중식 > 중화요리"
        );

        assertThat(result.placeName()).isEqualTo("영인성");
        assertThat(result.categoryName()).isEqualTo("음식점 > 중식 > 중화요리");
    }

    /**
     * 같은 카카오 응답에서 얻는 장소명과 카테고리 중 한쪽만 존재하는 상태를 거부하는지 확인한다.
     */
    @Test
    void 장소명과_카테고리는_함께_존재해야_한다() {
        assertThatThrownBy(() -> new KakaoPlaceMatchData(
                "영인성",
                null
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("placeName과 categoryName은 함께 존재하거나 함께 null이어야 합니다.");

        assertThatThrownBy(() -> new KakaoPlaceMatchData(
                null,
                "음식점 > 중식 > 중화요리"
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("placeName과 categoryName은 함께 존재하거나 함께 null이어야 합니다.");
    }

    /**
     * 값이 존재하는 장소명과 카테고리가 공백 문자열인 경우를 거부하는지 확인한다.
     */
    @Test
    void 장소명과_카테고리의_공백값을_거부한다() {
        assertThatThrownBy(() -> new KakaoPlaceMatchData(
                "  ",
                "음식점 > 중식 > 중화요리"
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("placeName은(는) 공백일 수 없습니다.");

        assertThatThrownBy(() -> new KakaoPlaceMatchData(
                "영인성",
                "  "
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("categoryName은(는) 공백일 수 없습니다.");
    }
}
