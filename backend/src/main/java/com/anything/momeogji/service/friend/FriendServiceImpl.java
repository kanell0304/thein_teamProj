package com.anything.momeogji.service.friend;

import com.anything.momeogji.dto.MemberDTO;
import com.anything.momeogji.dto.friend.FriendRequestResponse;
import com.anything.momeogji.entity.FriendRequest;
import com.anything.momeogji.entity.FriendRequestStatus;
import com.anything.momeogji.entity.Member;
import com.anything.momeogji.repository.FriendRequestRepository;
import com.anything.momeogji.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class FriendServiceImpl implements FriendService {

    private final FriendRequestRepository friendRequestRepository;
    private final MemberRepository memberRepository;

    @Override
    @Transactional
    public void sendRequest(Long requesterId, Long targetUserId) {
        if (requesterId.equals(targetUserId)) {
            throw new IllegalArgumentException("자기 자신에게는 친구 요청을 보낼 수 없습니다.");
        }

        Member requester = findMember(requesterId);
        Member receiver = findMember(targetUserId);

        // 이미 내가 보낸 요청(대기중이든 수락됐든)이 있는지 확인.
        friendRequestRepository.findByRequesterIdAndReceiverId(requesterId, targetUserId)
                .ifPresent(existing -> {
                    throw new IllegalArgumentException(existing.getStatus() == FriendRequestStatus.ACCEPTED
                            ? "이미 친구인 사용자입니다."
                            : "이미 요청을 보낸 사용자입니다.");
                });

        // 반대로 상대방이 이미 나에게 보낸 요청이 있는지 확인.
        Optional<FriendRequest> incoming = friendRequestRepository.findByRequesterIdAndReceiverId(targetUserId, requesterId);
        if (incoming.isPresent()) {
            FriendRequest existing = incoming.get();
            throw new IllegalArgumentException(existing.getStatus() == FriendRequestStatus.ACCEPTED
                    ? "이미 친구인 사용자입니다."
                    : "상대방이 이미 나에게 친구 요청을 보냈습니다. 받은 요청에서 수락해주세요.");
        }

        friendRequestRepository.save(FriendRequest.builder()
                .requester(requester)
                .receiver(receiver)
                .build());
    }

    @Override
    @Transactional(readOnly = true)
    public List<FriendRequestResponse> listReceivedRequests(Long memberId) {
        return friendRequestRepository.findByReceiverIdAndStatus(memberId, FriendRequestStatus.PENDING).stream()
                .map(request -> new FriendRequestResponse(
                        request.getId(),
                        request.getRequester().getId(),
                        request.getRequester().getNickname(),
                        request.getRequester().getProfileImageUrl()
                ))
                .toList();
    }

    @Override
    @Transactional
    public void accept(Long requestId, Long memberId) {
        FriendRequest request = findRequest(requestId);
        requireReceiver(request, memberId, "수락");
        request.accept();
    }

    @Override
    @Transactional
    public void reject(Long requestId, Long memberId) {
        FriendRequest request = findRequest(requestId);
        requireReceiver(request, memberId, "거절");
        friendRequestRepository.delete(request);
    }

    @Override
    @Transactional(readOnly = true)
    public List<MemberDTO> listFriends(Long memberId) {
        return friendRequestRepository.findAcceptedByMemberId(memberId).stream()
                .map(request -> {
                    Member other = request.getRequester().getId().equals(memberId)
                            ? request.getReceiver()
                            : request.getRequester();
                    return new MemberDTO(other.getId(), other.getNickname(), other.getProfileImageUrl());
                })
                .toList();
    }

    private void requireReceiver(FriendRequest request, Long memberId, String action) {
        if (!request.getReceiver().getId().equals(memberId)) {
            throw new IllegalArgumentException("본인이 받은 요청만 " + action + "할 수 있습니다.");
        }
    }

    private FriendRequest findRequest(Long requestId) {
        return friendRequestRepository.findById(requestId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 친구 요청입니다: " + requestId));
    }

    private Member findMember(Long memberId) {
        return memberRepository.findById(memberId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 사용자입니다: " + memberId));
    }
}
