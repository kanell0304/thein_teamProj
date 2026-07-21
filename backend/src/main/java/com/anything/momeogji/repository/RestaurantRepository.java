package com.anything.momeogji.repository;

import com.anything.momeogji.entity.recommendation.Restaurant;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface RestaurantRepository extends JpaRepository<Restaurant, Long> {

    Optional<Restaurant> findByKakaoPlaceId(String kakaoPlaceId);
}
