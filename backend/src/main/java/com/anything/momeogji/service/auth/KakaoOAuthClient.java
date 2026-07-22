package com.anything.momeogji.service.auth;

import com.anything.momeogji.dto.auth.KakaoUserProfile;

// 카카오 로그인(인가 코드 → 토큰 → 프로필)을 처리한다. 카카오 로컬 API와는 별개 제품("카카오 로그인")이라 콘솔에서 별도 활성화 + Redirect URI 등록이 필요하다.
public interface KakaoOAuthClient {

    String exchangeCodeForAccessToken(String code);

    KakaoUserProfile fetchUserProfile(String kakaoAccessToken);
}
