package com.anything.momeogji.dto.chat;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record ChatRoomCreateRequest(@NotBlank String name, @NotEmpty List<Long> participantIds) {
}
