package com.anything.momeogji.config.recommendation;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "kakao")
public record KakaoProperties(String restApiKey, String baseUrl) {
}
