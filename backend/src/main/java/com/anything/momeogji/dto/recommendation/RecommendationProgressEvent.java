package com.anything.momeogji.dto.recommendation;

/**
 * /topic/chatrooms/{chatRoomId}/recommendation-progress 로 브로드캐스트되는 이벤트.
 * status=STARTED면 result/errorMessage 둘 다 null, COMPLETED면 result만, FAILED면 errorMessage만 채워진다.
 * result는 RoundResponse라서 roundCandidateId가 포함돼 있어, 클라이언트가 이 이벤트만 받아도 바로 투표를 보낼 수 있다.
 */
public record RecommendationProgressEvent(
        RecommendationProgressStatus status,
        RoundResponse result,
        String errorMessage
) {
}
