package com.anything.momeogji.repository;

import com.anything.momeogji.entity.recommendation.Restaurant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface RestaurantRepository extends JpaRepository<Restaurant, Long> {

    Optional<Restaurant> findByKakaoPlaceId(String kakaoPlaceId);

    @Query("""
            select distinct restaurant.name
            from Restaurant restaurant
            where restaurant.name is not null
              and trim(restaurant.name) <> ''
              and (restaurant.category is null or restaurant.category <> 'SYSTEM')
            order by restaurant.name asc
            """)
    List<String> findKeywordCandidateNames();
}
