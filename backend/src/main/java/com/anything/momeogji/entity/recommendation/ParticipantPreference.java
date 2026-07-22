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

/** 참여자가 최초 한 번 제출한 개인 옵션과 해당 모임의 마이데이터 활용 동의를 저장한다. */
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

    /** 해당 모임에서 본인이 카드 마이데이터 활용에 동의했는지 여부. */
    @Builder.Default
    @Column(name = "mydata_consent", nullable = false, columnDefinition = "boolean default false")
    private boolean mydataConsent = false;

}
