package com.anything.momeogji.repository;

import com.anything.momeogji.entity.recommendation.Meetup;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.List;
import java.time.LocalDateTime;
import com.anything.momeogji.entity.recommendation.MeetupStatus;

public interface MeetupRepository extends JpaRepository<Meetup, Long> {

    Optional<Meetup> findFirstByChatRoomIdOrderByIdDesc(Long chatRoomId);

    List<Meetup> findByStatusAndVoteDeadlineAtLessThanEqual(MeetupStatus status, LocalDateTime deadline);
}
