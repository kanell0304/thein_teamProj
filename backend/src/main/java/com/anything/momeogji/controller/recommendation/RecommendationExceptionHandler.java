package com.anything.momeogji.controller.recommendation;

import com.anything.momeogji.exception.recommendation.AiRecommendationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

@RestControllerAdvice(basePackages = "com.anything.momeogji.controller.recommendation")
public class RecommendationExceptionHandler {

    @ExceptionHandler(AiRecommendationException.class)
    public ResponseEntity<Map<String, String>> handleAiError(AiRecommendationException ex) {
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of("error", ex.getMessage()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleInvalidInput(IllegalArgumentException ex) {
        return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
    }
}
