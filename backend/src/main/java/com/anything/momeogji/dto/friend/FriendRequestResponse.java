package com.anything.momeogji.dto.friend;

public record FriendRequestResponse(
        Long requestId,
        Long requesterId,
        String requesterNickname,
        String requesterProfileImageUrl
) {
}
