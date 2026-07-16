package com.anything.momeogji.dto.auth;

public record TokenResponse(String accessToken, Long memberId, String nickname) {
}
