package com.anything.momeogji.service.auth;

import com.anything.momeogji.config.auth.JwtProperties;
import com.anything.momeogji.entity.MemberRole;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;

/** 자체 발급 액세스 토큰(JWT)의 생성/검증을 담당한다. 리프레시 토큰은 아직 다루지 않는다. */
@Component
public class JwtTokenProvider {

    private static final String ROLE_CLAIM = "role";

    private final SecretKey key;
    private final long accessTokenExpirationMillis;

    public JwtTokenProvider(JwtProperties properties) {
        this.key = Keys.hmacShaKeyFor(properties.secret().getBytes(StandardCharsets.UTF_8));
        this.accessTokenExpirationMillis = properties.accessTokenExpirationMinutes() * 60_000L;
    }

    public String createAccessToken(Long memberId, MemberRole role) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(String.valueOf(memberId))
                .claim(ROLE_CLAIM, role.name())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusMillis(accessTokenExpirationMillis)))
                .signWith(key)
                .compact();
    }

    /** 서명/만료가 유효하지 않으면 빈 Optional을 반환한다(예외를 던지지 않음). */
    public Optional<Claims> parseClaims(String token) {
        try {
            return Optional.of(Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload());
        } catch (JwtException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    public Long extractMemberId(Claims claims) {
        return Long.valueOf(claims.getSubject());
    }

    public MemberRole extractRole(Claims claims) {
        return MemberRole.valueOf(claims.get(ROLE_CLAIM, String.class));
    }
}
