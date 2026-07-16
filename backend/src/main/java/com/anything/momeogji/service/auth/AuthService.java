package com.anything.momeogji.service.auth;

import com.anything.momeogji.dto.auth.TokenResponse;

public interface AuthService {

    /** 카카오 로그인 인가 코드로 실제 로그인을 처리한다. */
    TokenResponse loginWithKakao(String code);

    /** 개발 전용: 실제 카카오 인증 없이 kakaoId로 회원을 조회/생성하고 토큰을 발급한다. */
    TokenResponse devLogin(String kakaoId, String nickname);
}
