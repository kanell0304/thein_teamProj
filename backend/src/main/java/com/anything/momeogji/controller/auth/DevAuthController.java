package com.anything.momeogji.controller.auth;

import com.anything.momeogji.dto.auth.DevLoginRequest;
import com.anything.momeogji.dto.auth.TokenResponse;
import com.anything.momeogji.service.auth.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 개발 전용 로그인 우회. 실제 카카오 인증 없이 kakaoId만으로 토큰을 발급받아 로컬에서 다른 API를 빠르게 테스트하기 위한 것.
 * TODO: 배포 프로필이 생기면 이 컨트롤러 자체를 dev/local 프로필에서만 노출하도록 반드시 막아야 한다.
 */
@RestController
@RequestMapping("/api/dev/auth")
@RequiredArgsConstructor
public class DevAuthController {

    private final AuthService authService;

    @PostMapping("/login")
    public TokenResponse devLogin(@Valid @RequestBody DevLoginRequest request) {
        return authService.devLogin(request.kakaoId(), request.nickname());
    }
}
