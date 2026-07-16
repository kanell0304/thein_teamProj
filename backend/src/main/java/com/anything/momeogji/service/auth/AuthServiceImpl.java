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
                .orElseGet(() -> memberRepository.save(Member.builder()
                        .kakaoId(kakaoId)
                        .nickname(nickname)
                        .profileImageUrl(profileImageUrl)
                        .build()));
    }

    private TokenResponse issueToken(Member member) {
        String accessToken = jwtTokenProvider.createAccessToken(member.getId(), member.getRole());
        return new TokenResponse(accessToken, member.getId(), member.getNickname());
    }
}
