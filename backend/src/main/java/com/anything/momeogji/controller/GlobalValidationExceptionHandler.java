package com.anything.momeogji.controller;

import com.anything.momeogji.dto.common.ApiErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.http.converter.HttpMessageNotReadableException;

import java.util.LinkedHashMap;
import java.util.Map;

/** Validation 실패를 컨트롤러별 Map 응답이 아닌 하나의 공통 API 오류 형식으로 변환한다. */
@RestControllerAdvice
public class GlobalValidationExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleRequestValidation(
            MethodArgumentNotValidException exception,
            HttpServletRequest request
    ) {
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        for (FieldError fieldError : exception.getBindingResult().getFieldErrors()) {
            fieldErrors.putIfAbsent(fieldError.getField(), fieldError.getDefaultMessage());
        }

        return badRequest(
                "VALIDATION_FAILED",
                "요청값을 확인해주세요.",
                request,
                fieldErrors
        );
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiErrorResponse> handleConstraintViolation(
            ConstraintViolationException exception,
            HttpServletRequest request
    ) {
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        exception.getConstraintViolations().forEach(violation -> fieldErrors.putIfAbsent(
                violation.getPropertyPath().toString(),
                violation.getMessage()
        ));

        return badRequest(
                "CONSTRAINT_VIOLATION",
                "요청 조건을 확인해주세요.",
                request,
                fieldErrors
        );
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiErrorResponse> handleUnreadableBody(HttpServletRequest request) {
        return badRequest(
                "MALFORMED_REQUEST",
                "요청 본문 형식이 올바르지 않습니다.",
                request,
                Map.of()
        );
    }

    private ResponseEntity<ApiErrorResponse> badRequest(
            String code,
            String message,
            HttpServletRequest request,
            Map<String, String> fieldErrors
    ) {
        int status = HttpStatus.BAD_REQUEST.value();
        return ResponseEntity.badRequest().body(ApiErrorResponse.of(
                status,
                code,
                message,
                request.getRequestURI(),
                fieldErrors
        ));
    }
}
