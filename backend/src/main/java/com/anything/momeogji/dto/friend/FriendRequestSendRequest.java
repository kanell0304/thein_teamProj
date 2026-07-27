package com.anything.momeogji.dto.friend;

import jakarta.validation.constraints.NotNull;

public record FriendRequestSendRequest(@NotNull Long targetUserId) {
}
