package com.anything.momeogji.config.auth;

import com.anything.momeogji.entity.MemberRole;
import com.anything.momeogji.service.auth.JwtTokenProvider;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

/**
 * Authorization: Bearer {token} 헤더를 읽어 SecurityContext에 인증 정보를 채운다.
 * 토큰이 없거나 유효하지 않으면 그냥 통과시키고(인증 안 된 상태), 실제 차단은 SecurityConfig의 authorizeHttpRequests가 담당한다.
 */
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtTokenProvider jwtTokenProvider;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        resolveToken(request)
                .flatMap(jwtTokenProvider::parseClaims)
                .ifPresent(this::authenticate);

        filterChain.doFilter(request, response);
    }

    private void authenticate(Claims claims) {
        Long memberId = jwtTokenProvider.extractMemberId(claims);
        MemberRole role = jwtTokenProvider.extractRole(claims);
        List<SimpleGrantedAuthority> authorities = List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));

        var authentication = new UsernamePasswordAuthenticationToken(memberId, null, authorities);
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    private Optional<String> resolveToken(HttpServletRequest request) {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header != null && header.startsWith(BEARER_PREFIX)) {
            return Optional.of(header.substring(BEARER_PREFIX.length()));
        }
        return Optional.empty();
    }
}
