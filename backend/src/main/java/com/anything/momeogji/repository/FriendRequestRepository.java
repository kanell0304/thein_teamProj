package com.anything.momeogji.repository;

import com.anything.momeogji.entity.FriendRequest;
import com.anything.momeogji.entity.FriendRequestStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface FriendRequestRepository extends JpaRepository<FriendRequest, Long> {

    Optional<FriendRequest> findByRequesterIdAndReceiverId(Long requesterId, Long receiverId);

    List<FriendRequest> findByReceiverIdAndStatus(Long receiverId, FriendRequestStatus status);

    @Query("""
            select fr from FriendRequest fr
            where fr.status = com.anything.momeogji.entity.FriendRequestStatus.ACCEPTED
              and (fr.requester.id = :memberId or fr.receiver.id = :memberId)
            """)
    List<FriendRequest> findAcceptedByMemberId(@Param("memberId") Long memberId);
}
