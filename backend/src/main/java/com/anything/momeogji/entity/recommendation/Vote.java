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

import java.time.LocalDateTime;

/** 참여자의 후보 투표 1건. 같은 후보에 중복 투표는 막되, 서로 다른 후보에는 중복 투표(복수 선택)할 수 있다. */
@Entity
@Table(name = "votes", uniqueConstraints = @UniqueConstraint(columnNames = {"round_candidate_id", "meetup_participant_id"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class Vote {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "round_candidate_id", nullable = false)
    private RoundCandidate roundCandidate;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "meetup_participant_id", nullable = false)
    private MeetupParticipant meetupParticipant;

    @Column(name = "voted_at", nullable = false)
    private LocalDateTime votedAt;
}
