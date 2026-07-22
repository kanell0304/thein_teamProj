package com.anything.momeogji.dto.recommendation;

import java.util.List;

/** 참여자가 개인 선호를 제출할 때마다 채팅방에 브로드캐스트되는 진행상황 이벤트. */
public record MeetupProgressEvent(
        Long meetupId,
        List<ParticipantSummaryResponse> participants
) {
}
