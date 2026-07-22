package com.anything.momeogji.dto.recommendation;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 채팅방 안에서 모임(음식점 결정 세션)을 시작할 때 보내는 요청. 아직 AI 추천은 하지 않는다.
 * 이 요청 자체가 곧 초대다 - participantIds로 지정한 사람들은 PENDING 상태의 참여자로 즉시 등록된다.
 *
 * @param personalOptionDeadlineAt 참가자 개인 조건 입력 마감 시각. 재접속 시 공지와 타이머 복원에 사용한다.
 * @param voteDeadlineAt 기존 클라이언트 호환용 절대 마감 시각. 새 클라이언트는 voteDurationMinutes를 사용한다.
 * @param voteDurationMinutes 추천 완료 후 투표를 진행할 시간(3~60분). 비워두면 10분이다.
 */
public record MeetupCreateRequest(
        @NotNull Long chatRoomId,
        @NotNull @Valid CommonOptionRequest commonOption,
        @NotNull LocalDateTime personalOptionDeadlineAt,
        LocalDateTime voteDeadlineAt,
        @Min(3) @Max(60) Integer voteDurationMinutes,
        @NotEmpty List<Long> participantIds
) {
    public MeetupCreateRequest {
        voteDurationMinutes = voteDurationMinutes == null ? 10 : voteDurationMinutes;
    }
}
