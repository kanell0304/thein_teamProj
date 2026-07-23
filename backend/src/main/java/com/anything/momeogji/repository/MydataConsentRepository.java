package com.anything.momeogji.repository;

import com.anything.momeogji.entity.recommendation.MydataConsent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface MydataConsentRepository extends JpaRepository<MydataConsent, Long> {

    Optional<MydataConsent> findByMeetupParticipantId(Long meetupParticipantId);
}
