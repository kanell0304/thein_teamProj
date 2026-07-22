package com.anything.momeogji.repository;

import com.anything.momeogji.entity.recommendation.Meetup;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface MeetupRepository extends JpaRepository<Meetup, Long> {

    Optional<Meetup> findFirstByChatRoomIdOrderByIdDesc(Long chatRoomId);
}
