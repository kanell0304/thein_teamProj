package com.anything.momeogji.dto.recommendation;

import java.time.LocalDateTime;

/** 채팅방 상단 공지에 표시할 최종 결과. */
public record FinalNoticeResponse(
        String restaurantName,
        String roadAddress,
        String address,
        Double latitude,
        Double longitude,
        String imageUrl,
        int participantCount,
        LocalDateTime meetingTime
) {
}
