package com.anything.momeogji.repository;

import com.anything.momeogji.entity.recommendation.MeetupParticipant;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface MeetupParticipantRepository extends JpaRepository<MeetupParticipant, Long> {

    Optional<MeetupParticipant> findByMeetupIdAndUserId(Long meetupId, Long userId);

    long countByMeetupId(Long meetupId);
}
