package com.anything.momeogji.dto.recommendation;

import java.util.List;

/** 회차 생성/투표/투표취소/조회 응답과 vote-updates 웹소켓 브로드캐스트에서 공통으로 쓰는 응답 형태. */
public record RoundResponse(
        Long meetupId,
        Long roundId,
        int roundNo,
        int participantCount,
        int votedParticipantCount,
        List<CandidateSummary> candidates
) {
}
