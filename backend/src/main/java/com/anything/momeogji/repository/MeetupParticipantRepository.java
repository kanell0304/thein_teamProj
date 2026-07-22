package com.anything.momeogji.repository;

import com.anything.momeogji.entity.recommendation.MeetupParticipant;
import com.anything.momeogji.entity.recommendation.SubmissionStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface MeetupParticipantRepository extends JpaRepository<MeetupParticipant, Long> {

    Optional<MeetupParticipant> findByMeetupIdAndUserId(Long meetupId, Long userId);

    long countByMeetupId(Long meetupId);

    List<MeetupParticipant> findByMeetupId(Long meetupId);

    long countByMeetupIdAndSubmissionStatus(Long meetupId, SubmissionStatus submissionStatus);
}
