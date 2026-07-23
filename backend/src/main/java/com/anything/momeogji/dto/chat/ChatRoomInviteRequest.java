package com.anything.momeogji.dto.chat;

import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record ChatRoomInviteRequest(@NotEmpty List<Long> memberIds) {
}
