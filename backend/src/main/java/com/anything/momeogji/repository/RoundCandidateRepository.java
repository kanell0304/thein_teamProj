package com.anything.momeogji.repository;

import com.anything.momeogji.entity.recommendation.RoundCandidate;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RoundCandidateRepository extends JpaRepository<RoundCandidate, Long> {

    List<RoundCandidate> findByRoundId(Long roundId);

    List<RoundCandidate> findByRound_Meetup_Id(Long meetupId);
}
