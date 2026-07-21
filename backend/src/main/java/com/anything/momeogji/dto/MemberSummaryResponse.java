package com.anything.momeogji.dto;

/** 친구 목록과 참가자 선택 화면에서 사용하는 최소 회원 정보. */
public record MemberSummaryResponse(
        Long id,
        String nickname,
        String profileImageUrl
) {
}
