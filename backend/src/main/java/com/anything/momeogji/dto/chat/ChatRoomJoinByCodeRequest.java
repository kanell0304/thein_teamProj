package com.anything.momeogji.dto.chat;

import jakarta.validation.constraints.NotBlank;

public record ChatRoomJoinByCodeRequest(@NotBlank String code) {
}
