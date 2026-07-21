package com.anything.momeogji.repository;

import com.anything.momeogji.entity.recommendation.FinalNotice;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface FinalNoticeRepository extends JpaRepository<FinalNotice, Long> {

    Optional<FinalNotice> findByMeetupId(Long meetupId);
}
