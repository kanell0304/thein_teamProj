package com.anything.momeogji.dto.recommendation;

/** round는 이 제출로 전원 제출이 완료되어 AI 추천이 즉시 실행된 경우에만 채워진다. */
public record PreferenceSubmitResponse(
        Long meetupId,
        int submittedCount,
        int totalCount,
        boolean recommendationTriggered,
        RoundResponse round
) {
}
