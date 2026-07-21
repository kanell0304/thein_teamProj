package com.anything.momeogji.dto.recommendation;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * 음식점 추천 요청. 컨트롤러에 직접 바인딩되지 않는 내부 조립용 DTO — MeetupRecommendationRoundService가
 * Meetup에 저장된 공통 옵션 + 회차별 personalOptions + 파생된 제외 목록을 조합해서 만든다.
 *
 * @param commonOption           주최자가 입력한 공통 옵션(목적지, 약속 시간, 목적 등)
 * @param personalOptions        참여자별 개인 옵션 리스트. 1명당 1건, 최소 1명 이상 필요.
 * @param excludedRestaurantIds  재추천 시 이전 회차 후보의 RestaurantRecommendation.id를 그대로 넣으면 이번 검색 후보에서 제외한다. 최초 추천이면 비워두거나 생략.
 * @param preferenceNote         재추천 시 사용자가 직접 입력하는 우선순위 힌트. 예: "가성비 위주로", "웨이팅 적은 곳 위주로". 없으면 생략(null).
 */
public record RecommendationRequest(
        @NotNull @Valid CommonOptionRequest commonOption,
        @NotEmpty @Valid List<PersonalOptionRequest> personalOptions,
        List<String> excludedRestaurantIds,
        String preferenceNote
) {
    public RecommendationRequest {
        excludedRestaurantIds = excludedRestaurantIds == null ? List.of() : List.copyOf(excludedRestaurantIds);
    }
}
