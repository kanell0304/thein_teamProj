package com.anything.momeogji.repository;

import com.anything.momeogji.entity.recommendation.Vote;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.List;

public interface VoteRepository extends JpaRepository<Vote, Long> {

    long countByRoundCandidateId(Long roundCandidateId);

    Optional<Vote> findByRoundCandidateIdAndMeetupParticipantId(Long roundCandidateId, Long meetupParticipantId);

    void deleteByRoundCandidateIdAndMeetupParticipantId(Long roundCandidateId, Long meetupParticipantId);

    List<Vote> findByRoundCandidateId(Long roundCandidateId);

    List<Vote> findByRoundCandidateRoundIdAndMeetupParticipantId(Long roundId, Long meetupParticipantId);

    /** 이 회차에서 최소 한 후보에라도 투표한 서로 다른 참여자 수. 전원 투표 완료 여부(자동 확정) 판단에 쓴다. */
    @Query("select count(distinct v.meetupParticipant.id) from Vote v where v.roundCandidate.round.id = :roundId")
    long countDistinctVotersByRoundId(@Param("roundId") Long roundId);
}
