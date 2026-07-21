package com.anything.momeogji.dto.common;

import java.time.LocalDateTime;
import java.util.Map;

/** 프론트엔드가 모든 API 오류를 같은 형태로 처리할 수 있도록 제공하는 공통 응답. */
public record ApiErrorResponse(
        LocalDateTime timestamp,
        int status,
        String code,
        String message,
        String path,
        Map<String, String> fieldErrors
) {
    public static ApiErrorResponse of(
            int status,
            String code,
            String message,
            String path,
            Map<String, String> fieldErrors
    ) {
        return new ApiErrorResponse(
                LocalDateTime.now(),
                status,
                code,
                message,
                path,
                Map.copyOf(fieldErrors)
        );
    }
}
