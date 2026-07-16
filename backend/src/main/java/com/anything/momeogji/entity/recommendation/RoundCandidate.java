package com.anything.momeogji.entity.recommendation;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 한 회차에서 AI가 고른 음식점 1곳(회차당 3건). */
@Entity
@Table(name = "round_candidates", uniqueConstraints = @UniqueConstraint(columnNames = {"round_id", "restaurant_id"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class RoundCandidate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "round_id", nullable = false)
    private RecommendationRound round;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "restaurant_id", nullable = false)
    private Restaurant restaurant;

    // "rank"는 PostgreSQL 예약어라 컬럼명을 rank_no로 사용한다.
    @Column(name = "rank_no", nullable = false)
    private Integer rankNo;

    @Column(name = "distance_meters")
    private Integer distanceMeters;

    @Column(columnDefinition = "text")
    private String reason;
}
