package com.anything.momeogji.service.recommendation;

import com.anything.momeogji.mydata.processing.model.MyDataRestaurantData;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 모임·사용자별 MyData 결과 저장과 사용자 구분 제거 동작을 검증한다.
 */
class MeetupMyDataResultStoreTest {

    private final MeetupMyDataResultStore store =
            new MeetupMyDataResultStore();

    /**
     * 사용자 입력 순서로 결과를 연결하고 서로 다른 사용자의 동일 음식점 중복을 유지하는지 확인한다.
     */
    @Test
    void 사용자별_결과를_하나의_목록으로_연결한다() {
        MyDataRestaurantData duplicatedRestaurant =
                new MyDataRestaurantData("영인성", "중식 > 중화요리");
        store.save(10L, 1L, List.of(
                duplicatedRestaurant,
                new MyDataRestaurantData("상무초밥", "일식 > 초밥")
        ));
        store.save(10L, 2L, List.of(
                duplicatedRestaurant,
                new MyDataRestaurantData("정돈", "일식 > 돈까스")
        ));

        List<MyDataRestaurantData> result =
                store.findAll(10L, List.of(2L, 1L));

        assertThat(result).containsExactly(
                duplicatedRestaurant,
                new MyDataRestaurantData("정돈", "일식 > 돈까스"),
                duplicatedRestaurant,
                new MyDataRestaurantData("상무초밥", "일식 > 초밥")
        );
        assertThatThrownBy(() -> result.add(duplicatedRestaurant))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    /**
     * 한 모임을 정리해도 다른 모임의 결과는 유지되는지 확인한다.
     */
    @Test
    void 확정된_모임의_결과만_제거한다() {
        MyDataRestaurantData restaurant =
                new MyDataRestaurantData("영인성", "중식 > 중화요리");
        store.save(10L, 1L, List.of(restaurant));
        store.save(20L, 1L, List.of(restaurant));

        store.clear(10L);

        assertThat(store.findAll(10L, List.of(1L))).isEmpty();
        assertThat(store.findAll(20L, List.of(1L)))
                .containsExactly(restaurant);
    }
}
