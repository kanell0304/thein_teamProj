package com.anything.momeogji.config.auth;

import com.anything.momeogji.entity.MemberRole;
import com.anything.momeogji.service.auth.JwtTokenProvider;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * STOMP CONNECT 프레임의 Authorization 헤더를 읽어 REST와 같은 JWT로 인증한다.
 * 브라우저의 WebSocket 핸드셰이크 자체에는 커스텀 헤더를 못 실으므로(JS WebSocket API 제약),
 * 연결이 수립된 뒤 STOMP 프로토콜 레벨의 CONNECT 프레임에 토큰을 실어 보내는 방식을 쓴다.
 * 토큰이 없거나 유효하지 않으면 연결 자체를 거부한다.
 */
@Component
@RequiredArgsConstructor
public class StompAuthChannelInterceptor implements ChannelInterceptor {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtTokenProvider jwtTokenProvider;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);

        if (accessor != null && StompCommand.CONNECT.equals(accessor.getCommand())) {
            Claims claims = resolveToken(accessor)
                    .flatMap(jwtTokenProvider::parseClaims)
                    .orElseThrow(() -> new MessagingException("유효한 인증 토큰이 없습니다."));

            Long memberId = jwtTokenProvider.extractMemberId(claims);
            MemberRole role = jwtTokenProvider.extractRole(claims);
            List<SimpleGrantedAuthority> authorities = List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));

            accessor.setUser(new UsernamePasswordAuthenticationToken(memberId, null, authorities));
        }

        return message;
    }

    private Optional<String> resolveToken(StompHeaderAccessor accessor) {
        String header = accessor.getFirstNativeHeader(HttpHeaders.AUTHORIZATION);
        if (header != null && header.startsWith(BEARER_PREFIX)) {
            return Optional.of(header.substring(BEARER_PREFIX.length()));
        }
        return Optional.empty();
    }
}
