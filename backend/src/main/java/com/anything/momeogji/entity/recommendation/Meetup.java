package com.anything.momeogji.entity.recommendation;

import com.anything.momeogji.entity.ChatRoom;
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
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 채팅방 안에서 시작하는 음식점 결정 세션 1건. CommonOptionRequest와 1:1로 매칭된다. */
@Entity
@Table(name = "meetups")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class Meetup {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "chat_room_id", nullable = false)
    private ChatRoom chatRoom;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "host_user_id", nullable = false)
    private Member hostUser;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private MeetupStatus status;

    @Column(name = "destination_name", nullable = false, length = 100)
    private String destinationName;

    @Column(name = "destination_latitude", nullable = false, precision = 10, scale = 7)
    private BigDecimal destinationLatitude;

    @Column(name = "destination_longitude", nullable = false, precision = 10, scale = 7)
    private BigDecimal destinationLongitude;

    @Column(name = "meeting_time", nullable = false)
    private LocalDateTime meetingTime;

    @Column(nullable = false, length = 50)
    private String purpose;

    @Column(name = "vote_deadline_at")
    private LocalDateTime voteDeadlineAt;
}
