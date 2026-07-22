package com.anything.momeogji.controller.auth;

import com.anything.momeogji.exception.auth.AuthException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

@RestControllerAdvice(basePackages = "com.anything.momeogji.controller.auth")
public class AuthExceptionHandler {

    // 권한 인증이 잘못되었다면 BAD_GATEWAY 예외 발생
    @ExceptionHandler(AuthException.class)
    public ResponseEntity<Map<String, String>> handleAuthError(AuthException ex) {
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of("error", ex.getMessage()));
    }
}
