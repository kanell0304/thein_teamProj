package com.anything.momeogji.controller.auth;

import com.anything.momeogji.dto.auth.KakaoLoginRequest;
import com.anything.momeogji.dto.auth.TokenResponse;
import com.anything.momeogji.service.auth.AuthService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Auth [인증]")
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    // 프론트가 카카오 로그인 후 redirect_uri로 받은 인가 코드를 그대로 넘기면, 우리 서버가 발급한 액세스 토큰을 돌려준다.
    @PostMapping("/kakao/login")
    public TokenResponse kakaoLogin(@Valid @RequestBody KakaoLoginRequest request) {
        return authService.loginWithKakao(request.code());
    }
}
