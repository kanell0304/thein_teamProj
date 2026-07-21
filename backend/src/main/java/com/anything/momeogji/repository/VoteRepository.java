package com.anything.momeogji.repository;

import com.anything.momeogji.entity.recommendation.Vote;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface VoteRepository extends JpaRepository<Vote, Long> {

    long countByRoundCandidateId(Long roundCandidateId);

    Optional<Vote> findByRoundCandidateIdAndMeetupParticipantId(Long roundCandidateId, Long meetupParticipantId);

    void deleteByRoundCandidateIdAndMeetupParticipantId(Long roundCandidateId, Long meetupParticipantId);
}
