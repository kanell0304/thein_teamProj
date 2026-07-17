package com.anything.momeogji.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/** 웹소켓 핸드셰이크(/ws)를 허용할 프론트 origin 목록. 배포 시 실제 프론트 도메인으로 덮어써야 한다. */
@ConfigurationProperties(prefix = "app.cors")
public record AppCorsProperties(List<String> allowedOrigins) {
}
