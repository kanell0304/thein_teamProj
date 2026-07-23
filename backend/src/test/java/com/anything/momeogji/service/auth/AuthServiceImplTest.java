package com.anything.momeogji.service.auth;

import com.anything.momeogji.dto.auth.KakaoUserProfile;
import com.anything.momeogji.dto.auth.TokenResponse;
import com.anything.momeogji.entity.Member;
import com.anything.momeogji.entity.MemberRole;
import com.anything.momeogji.repository.MemberRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

/** 카카오 닉네임 동의 여부에 따른 회원 닉네임 확정 로직을 검증한다. */
@ExtendWith(MockitoExtension.class)
class AuthServiceImplTest {

    @Mock
    private KakaoOAuthClient kakaoOAuthClient;
    @Mock
    private MemberRepository memberRepository;
    @Mock
    private JwtTokenProvider jwtTokenProvider;

    private AuthServiceImpl authService;

    @BeforeEach
    void setUp() {
        authService = new AuthServiceImpl(kakaoOAuthClient, memberRepository, jwtTokenProvider);
        given(kakaoOAuthClient.exchangeCodeForAccessToken("auth-code")).willReturn("kakao-access-token");
        given(memberRepository.findByKakaoId("12345")).willReturn(Optional.empty());
        given(jwtTokenProvider.createAccessToken(any(), any())).willReturn("jwt-token");
    }

    @Test
    void 닉네임_동의를_안_하면_ID를_붙인_임시_닉네임으로_가입한다() {
        given(kakaoOAuthClient.fetchUserProfile("kakao-access-token"))
                .willReturn(new KakaoUserProfile("12345", null, null));
        given(memberRepository.save(any(Member.class))).willReturn(
                Member.builder().id(77L).kakaoId("12345").nickname("카카오사용자").build());

        TokenResponse response = authService.loginWithKakao("auth-code");

        assertThat(response.nickname()).isEqualTo("카카오사용자77");
        assertThat(response.memberId()).isEqualTo(77L);
    }

    @Test
    void 닉네임_동의를_했으면_그대로_사용한다() {
        given(kakaoOAuthClient.fetchUserProfile("kakao-access-token"))
                .willReturn(new KakaoUserProfile("12345", "이경준", null));
        given(memberRepository.save(any(Member.class))).willReturn(
                Member.builder().id(5L).kakaoId("12345").nickname("이경준").build());

        TokenResponse response = authService.loginWithKakao("auth-code");

        assertThat(response.nickname()).isEqualTo("이경준");
    }

    @Test
    void 이미_가입된_회원이면_새로_만들지_않는다() {
        Member existing = Member.builder().id(1L).kakaoId("12345").nickname("이경준").role(MemberRole.USER).build();
        given(memberRepository.findByKakaoId("12345")).willReturn(Optional.of(existing));
        given(kakaoOAuthClient.fetchUserProfile("kakao-access-token"))
                .willReturn(new KakaoUserProfile("12345", null, null));

        TokenResponse response = authService.loginWithKakao("auth-code");

        assertThat(response.nickname()).isEqualTo("이경준");
        assertThat(response.memberId()).isEqualTo(1L);
    }
}
