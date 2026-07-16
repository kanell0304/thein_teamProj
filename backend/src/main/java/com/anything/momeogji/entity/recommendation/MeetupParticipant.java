package com.anything.momeogji.entity.recommendation;

import com.anything.momeogji.entity.Member;
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

/** 주최자가 선택한 대상 인원별 진행 상태. PersonalOptionRequest.participantId에 대응한다. */
@Entity
@Table(name = "meetup_participants", uniqueConstraints = @UniqueConstraint(columnNames = {"meetup_id", "user_id"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class MeetupParticipant {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "meetup_id", nullable = false)
    private Meetup meetup;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private Member user;

    @Enumerated(EnumType.STRING)
    @Column(name = "submission_status", nullable = false, length = 20)
    private SubmissionStatus submissionStatus;

    @Column(name = "confirmed_for_ai", nullable = false)
    private boolean confirmedForAi;
}
