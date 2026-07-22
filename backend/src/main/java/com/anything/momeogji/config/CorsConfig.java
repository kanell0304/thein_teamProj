package com.anything.momeogji.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * REST API(/api/**)에 대한 크로스 오리진 호출 허용 설정.
 * WebSocket 핸드셰이크(/ws) origin 허용은 WebSocketConfig가 별도로 관리한다 — 둘은 서로 다른 메커니즘이라
 * app.cors.allowed-origins 값은 같이 쓰되 설정 위치는 분리돼 있다.
 */
@Configuration
@RequiredArgsConstructor
public class CorsConfig {

    private final AppCorsProperties corsProperties;

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(corsProperties.allowedOrigins());
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", configuration);
        return source;
    }
}
