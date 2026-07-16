package com.anything.momeogji.service.auth;

import com.anything.momeogji.config.auth.JwtProperties;
import com.anything.momeogji.entity.MemberRole;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class JwtTokenProviderTest {

    private final JwtTokenProvider tokenProvider = new JwtTokenProvider(
            new JwtProperties("test-secret-key-for-jwt-must-be-long-enough-for-hs256", 60)
    );

    @Test
    void 발급한_토큰에서_memberId와_role을_그대로_복원한다() {
        String token = tokenProvider.createAccessToken(42L, MemberRole.ADMIN);

        Optional<Claims> claims = tokenProvider.parseClaims(token);

        assertThat(claims).isPresent();
        assertThat(tokenProvider.extractMemberId(claims.get())).isEqualTo(42L);
        assertThat(tokenProvider.extractRole(claims.get())).isEqualTo(MemberRole.ADMIN);
    }

    @Test
    void 서명이_다른_토큰은_검증에_실패한다() {
        JwtTokenProvider otherProvider = new JwtTokenProvider(
                new JwtProperties("different-secret-key-also-long-enough-for-hs256", 60)
        );
        String token = otherProvider.createAccessToken(1L, MemberRole.USER);

        Optional<Claims> claims = tokenProvider.parseClaims(token);

        assertThat(claims).isEmpty();
    }

    @Test
    void 만료된_토큰은_검증에_실패한다() {
        JwtTokenProvider expiringProvider = new JwtTokenProvider(
                new JwtProperties("test-secret-key-for-jwt-must-be-long-enough-for-hs256", -1)
        );
        String token = expiringProvider.createAccessToken(1L, MemberRole.USER);

        Optional<Claims> claims = expiringProvider.parseClaims(token);

        assertThat(claims).isEmpty();
    }

    @Test
    void 형식이_이상한_토큰은_예외없이_빈값을_반환한다() {
        Optional<Claims> claims = tokenProvider.parseClaims("not-a-real-jwt");

        assertThat(claims).isEmpty();
    }
}
