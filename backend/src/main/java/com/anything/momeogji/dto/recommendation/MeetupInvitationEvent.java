package com.anything.momeogji.dto.recommendation;

import java.util.List;

/** 모임이 생성되는 즉시 채팅방에 브로드캐스트되는 초대 이벤트. */
public record MeetupInvitationEvent(
        MeetupDetailResponse meetup,
        List<ParticipantSummaryResponse> participants
) {
}
