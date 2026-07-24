package com.anything.momeogji.controller.chat;

import com.anything.momeogji.dto.common.ApiErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

@RestControllerAdvice(basePackages = "com.anything.momeogji.controller.chat")
public class ChatExceptionHandler {

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiErrorResponse> handleInvalidInput(
            IllegalArgumentException exception,
            HttpServletRequest request
    ) {
        int status = HttpStatus.BAD_REQUEST.value();
        return ResponseEntity.badRequest().body(ApiErrorResponse.of(
                status,
                "INVALID_CHAT_REQUEST",
                exception.getMessage(),
                request.getRequestURI(),
                Map.of()
        ));
    }
}
