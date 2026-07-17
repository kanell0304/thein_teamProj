package com.anything.momeogji.repository;

import com.anything.momeogji.entity.recommendation.RecommendationRound;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface RecommendationRoundRepository extends JpaRepository<RecommendationRound, Long> {

    long countByMeetupId(Long meetupId);

    Optional<RecommendationRound> findFirstByMeetupIdOrderByRoundNoDesc(Long meetupId);
}
