package com.anything.momeogji.exception.recommendation;

/** OpenAI 응답이 비어있거나, 파싱에 실패하거나, 개수(3+2)가 맞지 않을 때 던진다. */
public class AiRecommendationException extends RuntimeException {

    public AiRecommendationException(String message) {
        super(message);
    }

    public AiRecommendationException(String message, Throwable cause) {
        super(message, cause);
    }
}
