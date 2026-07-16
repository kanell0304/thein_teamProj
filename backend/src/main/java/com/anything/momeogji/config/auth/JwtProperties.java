package com.anything.momeogji.config.auth;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "jwt")
public record JwtProperties(String secret, long accessTokenExpirationMinutes) {
}
