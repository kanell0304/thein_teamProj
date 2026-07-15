package com.anything.momeogji.config;

import jakarta.servlet.DispatcherType;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

// TODO: 실제 인증 방식(JWT 등)이 정해지면 교체해야 하는 임시 설정. 지금은 로컬 개발 중 /api/** 수동 테스트를 막지 않기 위한 용도.
@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(auth -> auth
                        // 컨트롤러에서 예외가 나면 컨테이너가 /error로 forward하는데, 이 디스패치도 인증을 요구하면
                        // 원래 상태코드(400/502 등)가 403으로 덮어써진다. 그걸 막기 위해 ERROR 디스패치는 항상 허용.
                        .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                        .requestMatchers("/api/**").permitAll()
                        .anyRequest().authenticated()
                );
        return http.build();
    }
}
