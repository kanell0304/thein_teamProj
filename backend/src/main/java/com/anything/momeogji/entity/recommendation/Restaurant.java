package com.anything.momeogji.entity.recommendation;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/** 카카오 로컬 API 검색 결과 캐시. RestaurantCandidate와 매칭된다. */
@Entity
@Table(name = "restaurants")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class Restaurant {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 카카오 장소 고유 id. 같은 음식점이 여러 회차에서 반복 추천돼도 중복 저장하지 않기 위한 키. */
    @Column(name = "kakao_place_id", nullable = false, unique = true, length = 50)
    private String kakaoPlaceId;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(length = 100)
    private String category;

    @Column(name = "road_address", length = 300)
    private String roadAddress;

    @Column(length = 300)
    private String address;

    @Column(precision = 10, scale = 7)
    private BigDecimal latitude;

    @Column(precision = 10, scale = 7)
    private BigDecimal longitude;
}
