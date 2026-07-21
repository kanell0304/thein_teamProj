package com.anything.momeogji.dto.recommendation;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;

/**
 * 채팅방 안에서 모임(음식점 결정 세션)을 시작할 때 보내는 요청. 아직 AI 추천은 하지 않는다.
 *
 * @param voteDeadlineAt 투표 마감 시각. 비워두면(null) 마감 없이 계속 투표할 수 있다.
 */
public record MeetupCreateRequest(
        @NotNull Long chatRoomId,
        @NotNull @Valid CommonOptionRequest commonOption,
        LocalDateTime voteDeadlineAt
) {
}
