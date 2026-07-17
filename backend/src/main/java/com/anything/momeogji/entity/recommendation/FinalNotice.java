package com.anything.momeogji.entity.recommendation;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/** 채팅방 상단에 고정되는 최종 결정 공지. FinalNoticeResponse와 매칭된다. */
@Entity
@Table(name = "final_notices")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class FinalNotice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "meetup_id", nullable = false, unique = true)
    private Meetup meetup;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "restaurant_id", nullable = false)
    private Restaurant restaurant;

    @Column(name = "meeting_datetime", nullable = false)
    private LocalDateTime meetingDatetime;

    /** 채팅방 상단 고정을 해제할 시각(=약속 시각). */
    @Column(name = "pinned_until")
    private LocalDateTime pinnedUntil;

    /** 확정 시점의 추천 후보(RoundCandidate) 이미지를 그대로 복사해둔다. 수정 API가 다시 이미지 API를 호출하지 않도록. */
    @Column(name = "image_url", length = 500)
    private String imageUrl;

    /** 호스트가 약속시간을 수정할 때 쓴다. pinnedUntil도 항상 meetingDatetime과 같이 움직이므로 같이 갱신한다. */
    public void updateMeetingDatetime(LocalDateTime meetingDatetime) {
        this.meetingDatetime = meetingDatetime;
        this.pinnedUntil = meetingDatetime;
    }
}
