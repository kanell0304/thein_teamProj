package com.anything.momeogji.repository;

import com.anything.momeogji.entity.recommendation.Restaurant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class RestaurantRepositoryTest {

    @Autowired
    private RestaurantRepository restaurantRepository;

    @Test
    void findsDistinctNonBlankNonSystemRestaurantNames() {
        String suffix = UUID.randomUUID().toString();
        String normalName = "키워드 음식점 " + suffix;
        String systemName = "시스템 음식점 " + suffix;

        restaurantRepository.saveAllAndFlush(List.of(
                restaurant(suffix + "-normal-1", normalName, "일식"),
                restaurant(suffix + "-normal-2", normalName, "일식"),
                restaurant(suffix + "-system", systemName, "SYSTEM"),
                restaurant(suffix + "-blank", "  ", "한식")
        ));

        List<String> names = restaurantRepository.findKeywordCandidateNames();

        assertThat(names).containsOnlyOnce(normalName);
        assertThat(names).doesNotContain(systemName);
        assertThat(names).noneMatch(String::isBlank);
    }

    private Restaurant restaurant(String kakaoPlaceId, String name, String category) {
        return Restaurant.builder()
                .kakaoPlaceId(kakaoPlaceId)
                .name(name)
                .category(category)
                .build();
    }
}
