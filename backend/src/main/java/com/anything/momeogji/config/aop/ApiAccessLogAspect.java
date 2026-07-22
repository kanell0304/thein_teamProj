package com.anything.momeogji.config.aop;

import jakarta.servlet.http.HttpServletRequest;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/** 요청 본문과 토큰을 제외하고 API 접근 결과와 실행시간만 기록한다. */
@Aspect
@Component
public class ApiAccessLogAspect {

    private static final Logger log = LoggerFactory.getLogger(ApiAccessLogAspect.class);

    @Around("within(@org.springframework.web.bind.annotation.RestController *)")
    public Object logApiAccess(ProceedingJoinPoint joinPoint) throws Throwable {
        long startedAt = System.nanoTime();
        HttpServletRequest request = currentRequest();
        String method = request == null ? "UNKNOWN" : request.getMethod();
        String uri = request == null ? "UNKNOWN" : request.getRequestURI();
        String handler = joinPoint.getSignature().toShortString();
        String memberId = currentMemberId();

        try {
            Object result = joinPoint.proceed();
            log.info(
                    "API access method={} uri={} handler={} memberId={} elapsedMs={} outcome=SUCCESS",
                    method,
                    uri,
                    handler,
                    memberId,
                    elapsedMilliseconds(startedAt)
            );
            return result;
        } catch (Throwable throwable) {
            log.warn(
                    "API access method={} uri={} handler={} memberId={} elapsedMs={} outcome=FAIL exception={}",
                    method,
                    uri,
                    handler,
                    memberId,
                    elapsedMilliseconds(startedAt),
                    throwable.getClass().getSimpleName()
            );
            throw throwable;
        }
    }

    private HttpServletRequest currentRequest() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            return attributes.getRequest();
        }
        return null;
    }

    private String currentMemberId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return "anonymous";
        }
        Object principal = authentication.getPrincipal();
        return principal instanceof Long memberId ? memberId.toString() : "anonymous";
    }

    private long elapsedMilliseconds(long startedAt) {
        return (System.nanoTime() - startedAt) / 1_000_000;
    }
}
