package com.anything.momeogji.exception.auth;

/** 카카오 인증/토큰 발급 과정에서 실패했을 때 던진다. */
public class AuthException extends RuntimeException {

    public AuthException(String message) {
        super(message);
    }

    public AuthException(String message, Throwable cause) {
        super(message, cause);
    }
}
