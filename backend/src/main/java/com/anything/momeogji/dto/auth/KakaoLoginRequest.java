package com.anything.momeogji.dto.auth;

import jakarta.validation.constraints.NotBlank;

/** 카카오 로그인 후 프론트가 redirect_uri로 받은 인가 코드. */
public record KakaoLoginRequest(@NotBlank String code) {
}
