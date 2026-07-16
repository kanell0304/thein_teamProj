package com.anything.momeogji.entity.recommendation;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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

/**
 * 추천 회차 1건. 최초 추천과 재추천(재호출)마다 하나씩 생긴다.
 * 이전 회차 제외 목록은 별도 컬럼 없이 같은 meetup의 이전 round_candidates를 조회해서 파생한다.
 */
@Entity
@Table(name = "recommendation_rounds", uniqueConstraints = @UniqueConstraint(columnNames = {"meetup_id", "round_no"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class RecommendationRound {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "meetup_id", nullable = false)
    private Meetup meetup;

    @Column(name = "round_no", nullable = false)
    private Integer roundNo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RecommendationRoundStatus status;

    /** 재추천 시 직접 입력한 우선순위. RecommendationRequest.preferenceNote와 매칭. */
    @Column(name = "preference_note", columnDefinition = "text")
    private String preferenceNote;
}
