package com.anything.momeogji.dto.chat;

import jakarta.validation.constraints.NotBlank;

public record ChatRoomCreateRequest(@NotBlank String name) {
}
