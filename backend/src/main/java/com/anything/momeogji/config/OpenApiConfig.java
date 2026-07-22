package com.anything.momeogji.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

// Swagger UI(/swagger-ui/index.html)의 Authorize 버튼에 JWT Bearer 스킴을 등록해,
// dev-login으로 발급받은 accessToken을 그대로 넣어 인증이 필요한 API도 바로 테스트할 수 있게 한다.
@Configuration
public class OpenApiConfig {

    private static final String BEARER_SCHEME_NAME = "bearerAuth";

    @Bean
    public OpenAPI openApi() {
        return new OpenAPI()
                .info(new Info().title("모먹지 API").description("채팅 기반 음식점 추천/투표 서비스 API").version("v1"))
                .addSecurityItem(new SecurityRequirement().addList(BEARER_SCHEME_NAME))
                .components(new Components().addSecuritySchemes(BEARER_SCHEME_NAME,
                        new SecurityScheme()
                                .name(BEARER_SCHEME_NAME)
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")));
    }
}
