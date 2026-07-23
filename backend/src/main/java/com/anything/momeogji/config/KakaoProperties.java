package com.anything.momeogji.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 카카오 로컬 API(음식점 검색)와 카카오 로그인이 같은 앱의 REST API 키를 공유하므로 하나로 묶어둔다.
 * baseUrl은 로컬 API용(dapi.kakao.com), 로그인은 kauth.kakao.com/kapi.kakao.com을 별도로 호출한다.
 */
@ConfigurationProperties(prefix = "kakao")
public record KakaoProperties(String restApiKey, String baseUrl, String loginRedirectUri, String clientSecret) {
}
