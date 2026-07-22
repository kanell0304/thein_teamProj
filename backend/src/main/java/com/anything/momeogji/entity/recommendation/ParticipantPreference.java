package com.anything.momeogji.entity.recommendation;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.List;

/** PersonalOptionRequest와 1:1로 매칭되는 개인 옵션. */
@Entity
@Table(name = "participant_preferences")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class ParticipantPreference {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "meetup_participant_id", nullable = false, unique = true)
    private MeetupParticipant meetupParticipant;

    @Column(name = "walk_minutes", nullable = false)
    private Integer walkMinutes;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "preferred_categories", nullable = false, columnDefinition = "jsonb")
    private List<String> preferredCategories;

    /** 1인당 예산 상한(원). null이면 무제한. */
    @Column(name = "budget_limit")
    private Integer budgetLimit;

    @Column(name = "parking_needed", nullable = false)
    private boolean parkingNeeded;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "excluded_foods", nullable = false, columnDefinition = "jsonb")
    private List<String> excludedFoods;

    /** null이면 상관없음. */
    @Column(length = 50)
    private String atmosphere;

    /** 같은 참여자가 재추천 등으로 선호를 다시 제출했을 때 최신값으로 덮어쓴다. */
    public void update(Integer walkMinutes, List<String> preferredCategories, Integer budgetLimit,
                        boolean parkingNeeded, List<String> excludedFoods, String atmosphere) {
        this.walkMinutes = walkMinutes;
        this.preferredCategories = preferredCategories;
        this.budgetLimit = budgetLimit;
        this.parkingNeeded = parkingNeeded;
        this.excludedFoods = excludedFoods;
        this.atmosphere = atmosphere;
    }
}
