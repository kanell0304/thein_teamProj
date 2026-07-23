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
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 마이데이터 활용 동의 및 목업 데이터 가공 상태.
 * v3 기획서 기준 스켈레톤이며, 실제 마이데이터/개인 선택 기능은 아직 구현되지 않았다.
 */
@Entity
@Table(name = "mydata_consents")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class MydataConsent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "meetup_participant_id", nullable = false, unique = true)
    private MeetupParticipant meetupParticipant;

    @Enumerated(EnumType.STRING)
    @Column(name = "consent_status", nullable = false, length = 20)
    private ConsentStatus consentStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "processing_status", nullable = false, length = 20)
    private ProcessingStatus processingStatus;

    /** 그룹 단위 추천 조건으로만 쓰이는 가공 결과. 개인 원본은 노출하지 않는다. */
    @Column(name = "processed_result", columnDefinition = "text")
    private String processedResult;

    /** 같은 참여자가 개인 선호를 다시 제출했을 때 동의·처리 상태를 최신값으로 덮어쓴다. */
    public void update(ConsentStatus consentStatus, ProcessingStatus processingStatus, String processedResult) {
        this.consentStatus = consentStatus;
        this.processingStatus = processingStatus;
        this.processedResult = processedResult;
    }
}
