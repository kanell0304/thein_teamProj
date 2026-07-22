package com.anything.momeogji.controller.auth;

import com.anything.momeogji.dto.auth.DevLoginRequest;
import com.anything.momeogji.dto.auth.TokenResponse;
import com.anything.momeogji.service.auth.AuthService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;


 // 개발 전용 로그인 우회. 실제 카카오 인증 없이 kakaoId만으로 토큰을 발급받아 로컬에서 다른 API를 빠르게 테스트하기 위한 것.
 // "dev" 프로필에서만 빈이 등록된다. 배포 시에는 SPRING_PROFILES_ACTIVE에 dev를 넣지 않으면(prod 등) 이 컨트롤러 자체가 사라진다.

@Tag(name = "Dev Auth [개발용 인증]")
@RestController
@RequestMapping("/api/dev/auth")
@RequiredArgsConstructor
@Profile("dev")
public class DevAuthController {

    private final AuthService authService;

    @PostMapping("/login")
    public TokenResponse devLogin(@Valid @RequestBody DevLoginRequest request) {
        return authService.devLogin(request.kakaoId(), request.nickname());
    }
}
