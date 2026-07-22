package com.anything.momeogji.dto.recommendation;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

/**
 * 추천 회차 생성 요청. 최초 추천/재추천 모두 이 요청으로 처리한다.
 * 제외할 이전 회차 후보 목록은 클라이언트가 넘길 필요 없이 서버가 같은 모임의 이전 round_candidates에서 자동으로 파생한다.
 */
public record RoundCreateRequest(
        @NotEmpty @Valid List<PersonalOptionRequest> personalOptions,
        String preferenceNote
) {
}
