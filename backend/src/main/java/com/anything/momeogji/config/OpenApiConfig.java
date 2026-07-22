package com.anything.momeogji.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Swagger UI에서 JWT를 한 번 입력해 보호 API를 시험할 수 있도록 OpenAPI를 설정한다. */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI momeokjiOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("오늘 모 먹지? API")
                        .version("v1")
                        .description("로그인, 채팅, 모임 조건, 추천과 투표 API 문서"))
                .components(new Components().addSecuritySchemes(
                        "bearerAuth",
                        new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                ));
    }
}
