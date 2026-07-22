package com.anything.momeogji.repository;

import com.anything.momeogji.entity.recommendation.ParticipantPreference;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ParticipantPreferenceRepository extends JpaRepository<ParticipantPreference, Long> {

    Optional<ParticipantPreference> findByMeetupParticipantId(Long meetupParticipantId);

    List<ParticipantPreference> findByMeetupParticipant_Meetup_Id(Long meetupId);
}
