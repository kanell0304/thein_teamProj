package com.anything.momeogji.dto.auth;

/** 카카오 사용자 정보 조회(GET /v2/user/me) 응답에서 우리가 쓰는 값만 추린 것. */
public record KakaoUserProfile(String kakaoId, String nickname, String profileImageUrl) {
}
