package com.anything.momeogji.dto.recommendation;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/** 한 참여자가 현재 회차에서 선택한 후보 전체. 기존 선택은 이 목록으로 교체된다. */
public record VoteSelectionRequest(
        @NotNull @Size(min = 1, max = 4) List<@NotNull Long> candidateIds
) {
    public VoteSelectionRequest {
        candidateIds = candidateIds == null ? List.of() : candidateIds.stream().distinct().toList();
    }
}
