package com.anything.momeogji.service.auth;

import com.anything.momeogji.config.KakaoProperties;
import com.anything.momeogji.dto.auth.KakaoUserProfile;
import com.anything.momeogji.exception.auth.AuthException;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.net.http.HttpClient;
import java.time.Duration;

@Component
public class KakaoOAuthClientImpl implements KakaoOAuthClient {

    private final RestClient authClient;
    private final RestClient apiClient;
    private final KakaoProperties properties;

    public KakaoOAuthClientImpl(RestClient.Builder restClientBuilder, KakaoProperties properties) {
        this.properties = properties;
        RestClient base = restClientBuilder.requestFactory(timeoutAwareRequestFactory()).build();
        this.authClient = base.mutate().baseUrl("https://kauth.kakao.com").build();
        this.apiClient = base.mutate().baseUrl("https://kapi.kakao.com").build();
    }

    private static ClientHttpRequestFactory timeoutAwareRequestFactory() {
        HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(Duration.ofSeconds(10));
        return factory;
    }

    @Override
    public String exchangeCodeForAccessToken(String code) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "authorization_code");
        form.add("client_id", properties.restApiKey());
        form.add("redirect_uri", properties.loginRedirectUri());
        form.add("code", code);
        // 카카오 콘솔에서 Client Secret을 사용 설정한 경우에만 필요 - 꺼져 있으면 값이 없어 보내지 않는다.
        if (properties.clientSecret() != null && !properties.clientSecret().isBlank()) {
            form.add("client_secret", properties.clientSecret());
        }

        try {
            KakaoTokenResponse response = authClient.post()
                    .uri("/oauth/token")
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(KakaoTokenResponse.class);

            if (response == null || response.accessToken() == null) {
                throw new AuthException("카카오 토큰 발급 응답이 비어 있습니다.");
            }
            return response.accessToken();
        } catch (RestClientException e) {
            throw new AuthException("카카오 토큰 발급에 실패했습니다: " + e.getMessage(), e);
        }
    }

    @Override
    public KakaoUserProfile fetchUserProfile(String kakaoAccessToken) {
        try {
            KakaoUserResponse response = apiClient.get()
                    .uri("/v2/user/me")
                    .headers(headers -> headers.setBearerAuth(kakaoAccessToken))
                    .retrieve()
                    .body(KakaoUserResponse.class);

            if (response == null || response.id() == null) {
                throw new AuthException("카카오 사용자 정보 응답이 비어 있습니다.");
            }

            KakaoUserResponse.Profile profile = response.kakaoAccount() != null ? response.kakaoAccount().profile() : null;
            String nickname = profile != null && profile.nickname() != null ? profile.nickname() : "카카오사용자";
            String profileImageUrl = profile != null ? profile.profileImageUrl() : null;

            return new KakaoUserProfile(String.valueOf(response.id()), nickname, profileImageUrl);
        } catch (RestClientException e) {
            throw new AuthException("카카오 사용자 정보 조회에 실패했습니다: " + e.getMessage(), e);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record KakaoTokenResponse(@JsonProperty("access_token") String accessToken) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record KakaoUserResponse(Long id, @JsonProperty("kakao_account") KakaoAccount kakaoAccount) {

        @JsonIgnoreProperties(ignoreUnknown = true)
        record KakaoAccount(Profile profile) {
        }

        @JsonIgnoreProperties(ignoreUnknown = true)
        record Profile(String nickname, @JsonProperty("profile_image_url") String profileImageUrl) {
        }
    }
}
