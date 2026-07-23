package com.anything.momeogji.service.auth;

import com.anything.momeogji.dto.auth.KakaoUserProfile;
import com.anything.momeogji.dto.auth.TokenResponse;
import com.anything.momeogji.entity.Member;
import com.anything.momeogji.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private static final String DEV_LOGIN_DEFAULT_NICKNAME = "테스트유저";
    // 카카오 닉네임 동의를 거부한 회원의 임시 닉네임. 여러 명이 겹치지 않도록 회원 ID를 뒤에 붙인다.
    private static final String KAKAO_DEFAULT_NICKNAME_PREFIX = "카카오사용자";

    private final KakaoOAuthClient kakaoOAuthClient;
    private final MemberRepository memberRepository;
    private final JwtTokenProvider jwtTokenProvider;

    @Override
    @Transactional
    public TokenResponse loginWithKakao(String code) {
        String kakaoAccessToken = kakaoOAuthClient.exchangeCodeForAccessToken(code);
        KakaoUserProfile profile = kakaoOAuthClient.fetchUserProfile(kakaoAccessToken);
        Member member = findOrCreateMember(profile.kakaoId(), profile.nickname(), profile.profileImageUrl());
        return issueToken(member);
    }

    @Override
    @Transactional
    public TokenResponse devLogin(String kakaoId, String nickname) {
        String resolvedNickname = (nickname == null || nickname.isBlank()) ? DEV_LOGIN_DEFAULT_NICKNAME : nickname;
        Member member = findOrCreateMember(kakaoId, resolvedNickname, null);
        return issueToken(member);
    }

    private Member findOrCreateMember(String kakaoId, String nickname, String profileImageUrl) {
        return memberRepository.findByKakaoId(kakaoId)
                .orElseGet(() -> createMember(kakaoId, nickname, profileImageUrl));
    }

    private Member createMember(String kakaoId, String nickname, String profileImageUrl) {
        boolean nicknameProvided = nickname != null && !nickname.isBlank();
        Member member = memberRepository.save(Member.builder()
                .kakaoId(kakaoId)
                .nickname(nicknameProvided ? nickname : KAKAO_DEFAULT_NICKNAME_PREFIX)
                .profileImageUrl(profileImageUrl)
                .build());

        // 닉네임 동의를 안 했다면, 자동 배정된 ID가 나온 지금에서야 겹치지 않는 닉네임으로 확정할 수 있다.
        if (!nicknameProvided) {
            member.updateNickname(KAKAO_DEFAULT_NICKNAME_PREFIX + member.getId());
        }
        return member;
    }

    private TokenResponse issueToken(Member member) {
        String accessToken = jwtTokenProvider.createAccessToken(member.getId(), member.getRole());
        return new TokenResponse(accessToken, member.getId(), member.getNickname(), member.getProfileImageUrl());
    }
}
