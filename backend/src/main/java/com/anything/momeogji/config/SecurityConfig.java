package com.anything.momeogji.config;

import com.anything.momeogji.config.auth.JwtAuthenticationFilter;
import jakarta.servlet.DispatcherType;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfigurationSource;

/**
 * 카카오 로그인 + 자체 발급 JWT 기반 인증.
 * /api/auth/**(실로그인), /api/dev/**(개발용 로그인 우회)만 열어두고 나머지 /api/**는 유효한 토큰이 있어야 통과한다.
 * /ws/**(WebSocket 핸드셰이크)도 HTTP 레벨에서는 열어둔다 — 실제 인증은 STOMP CONNECT 프레임에서
 * StompAuthChannelInterceptor가 따로 검증한다(브라우저 WebSocket API는 핸드셰이크에 커스텀 헤더를 못 실음).
 */
@Configuration
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final CorsConfigurationSource corsConfigurationSource;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .httpBasic(basic -> basic.disable())
                .formLogin(form -> form.disable())
                .cors(cors -> cors.configurationSource(corsConfigurationSource))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // 컨트롤러에서 예외가 나면 컨테이너가 /error로 forward하는데, 이 디스패치도 인증을 요구하면
                        // 원래 상태코드(400/502 등)가 403으로 덮어써진다. 그걸 막기 위해 ERROR 디스패치는 항상 허용.
                        .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers(
                                "/api/auth/**",
                                "/api/dev/**",
                                "/ws/**",
                                "/v3/api-docs/**",
                                "/swagger-ui/**",
                                "/swagger-ui.html"
                        ).permitAll()
                        .anyRequest().authenticated()
                )
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
