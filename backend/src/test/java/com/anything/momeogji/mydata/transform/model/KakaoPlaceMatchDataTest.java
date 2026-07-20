package com.anything.momeogji.mydata.transform.model;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link KakaoPlaceMatchData}가 최종 결과에 필요한 카카오 장소 계약을 강제하는지 검증한다.
 */
class KakaoPlaceMatchDataTest {

    /**
     * 검색 실패와 이름 미매칭에 사용하는 기본 객체가 미분류 상태를 명확히 표현하는지 확인한다.
     */
    @Test
    void 미매칭_기본값은_UNKNOWN과_빈_장소_정보를_가진다() {
        KakaoPlaceMatchData result = KakaoPlaceMatchData.unmatched();

        assertThat(result.categoryCode())
                .isEqualTo(KakaoPlaceMatchData.UNKNOWN_CATEGORY_CODE);
        assertThat(result.placeId()).isNull();
        assertThat(result.placeName()).isNull();
        assertThat(result.matchConfidence()).isZero();
        assertThat(result.longitude()).isNull();
        assertThat(result.latitude()).isNull();
    }

    /**
     * 양수와 음수 좌표가 모두 0 방향으로 소수점 다섯째 자리까지 절삭되는지 확인한다.
     */
    @Test
    void 좌표는_소수점_다섯째_자리까지_절삭한다() {
        KakaoPlaceMatchData positive = new KakaoPlaceMatchData(
                KakaoPlaceMatchData.RESTAURANT_CATEGORY_CODE,
                "1001",
                "영인성",
                100,
                new BigDecimal("127.123456"),
                new BigDecimal("37.987654")
        );
        KakaoPlaceMatchData negative = new KakaoPlaceMatchData(
                KakaoPlaceMatchData.CAFE_CATEGORY_CODE,
                "1002",
                "테스트 카페",
                90,
                new BigDecimal("-127.123456"),
                new BigDecimal("-37.987654")
        );

        assertThat(positive.longitude()).isEqualByComparingTo("127.12345");
        assertThat(positive.latitude()).isEqualByComparingTo("37.98765");
        assertThat(negative.longitude()).isEqualByComparingTo("-127.12345");
        assertThat(negative.latitude()).isEqualByComparingTo("-37.98765");
        assertThat(positive.longitude().scale()).isEqualTo(5);
        assertThat(positive.latitude().scale()).isEqualTo(5);
    }

    /**
     * 같은 최고 후보의 카테고리가 충돌한 경우 장소 정보가 있는 UNKNOWN 결과를 허용하는지 확인한다.
     */
    @Test
    void 장소를_찾았지만_카테고리가_충돌하면_UNKNOWN과_신뢰도를_함께_보존한다() {
        KakaoPlaceMatchData result = new KakaoPlaceMatchData(
                KakaoPlaceMatchData.UNKNOWN_CATEGORY_CODE,
                "1001",
                "영인성",
                100,
                null,
                null
        );

        assertThat(result.categoryCode())
                .isEqualTo(KakaoPlaceMatchData.UNKNOWN_CATEGORY_CODE);
        assertThat(result.placeId()).isEqualTo("1001");
        assertThat(result.matchConfidence()).isEqualTo(100);
    }

    /**
     * 장소·신뢰도·좌표의 존재 조합이 서로 모순되는 객체 생성을 거부하는지 확인한다.
     */
    @Test
    void 장소_신뢰도_좌표의_잘못된_조합을_거부한다() {
        assertThatThrownBy(() -> new KakaoPlaceMatchData(
                KakaoPlaceMatchData.RESTAURANT_CATEGORY_CODE,
                "1001",
                null,
                100,
                null,
                null
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("placeId와 placeName은 함께 존재하거나 함께 null이어야 합니다.");

        assertThatThrownBy(() -> new KakaoPlaceMatchData(
                KakaoPlaceMatchData.RESTAURANT_CATEGORY_CODE,
                null,
                null,
                90,
                null,
                null
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("matchConfidence가 0보다 크면 장소 정보가 필요합니다.");

        assertThatThrownBy(() -> new KakaoPlaceMatchData(
                KakaoPlaceMatchData.RESTAURANT_CATEGORY_CODE,
                "1001",
                "영인성",
                100,
                new BigDecimal("127.1"),
                null
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("longitude와 latitude는 함께 존재하거나 함께 null이어야 합니다.");
    }

    /**
     * 허용하지 않는 카테고리와 범위를 벗어난 좌표가 최종 값에 들어오지 못하는지 확인한다.
     */
    @Test
    void 허용하지_않는_카테고리와_좌표_범위를_거부한다() {
        assertThatThrownBy(() -> new KakaoPlaceMatchData(
                "CS2",
                "1001",
                "편의점",
                100,
                null,
                null
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("categoryCode는 FD6, CE7, UNKNOWN 중 하나여야 합니다.");

        assertThatThrownBy(() -> new KakaoPlaceMatchData(
                KakaoPlaceMatchData.RESTAURANT_CATEGORY_CODE,
                "1001",
                "영인성",
                100,
                new BigDecimal("180.000001"),
                new BigDecimal("37.1")
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("longitude이(가) 허용 범위를 벗어났습니다.");
    }
}
