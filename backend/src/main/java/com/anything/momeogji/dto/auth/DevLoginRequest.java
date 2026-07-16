package com.anything.momeogji.dto.auth;

import jakarta.validation.constraints.NotBlank;

/**
 * 개발 전용 로그인 우회 요청. 실제 카카오 인증 없이 kakaoId로 회원을 조회/생성해 토큰을 발급한다.
 * TODO: 배포 프로필이 생기면 이 엔드포인트 자체를 dev/local 프로필에서만 노출하도록 막아야 한다.
 */
public record DevLoginRequest(@NotBlank String kakaoId, String nickname) {
}
