package com.anything.momeogji.service.friend;

import com.anything.momeogji.dto.MemberDTO;
import com.anything.momeogji.dto.friend.FriendRequestResponse;

import java.util.List;

public interface FriendService {

    /** UID(회원 ID) 기준으로 친구 요청을 보낸다. 이미 친구이거나 대기 중인 요청이 있으면 예외를 던진다. */
    void sendRequest(Long requesterId, Long targetUserId);

    /** 아직 응답하지 않은, 내가 받은 친구 요청 목록을 조회한다. */
    List<FriendRequestResponse> listReceivedRequests(Long memberId);

    /** 본인이 받은 요청만 수락할 수 있다. */
    void accept(Long requestId, Long memberId);

    /** 본인이 받은 요청만 거절(삭제)할 수 있다. */
    void reject(Long requestId, Long memberId);

    /** 서로 수락이 완료된 친구 목록을 조회한다. */
    List<MemberDTO> listFriends(Long memberId);
}
