package com.anything.momeogji.mydata.retry;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * MyData 외부 호출의 1회 재시도 간격과 카카오 검색 회로 차단 기준을 제공한다.
 *
 * @param retryDelay 재시도 가능한 최초 실패 후 동일 요청을 다시 수행하기 전 대기 시간
 * @param kakaoCircuitFailureThreshold 카카오 검색 회로를 열 연속 최종 실패 횟수
 * @param kakaoCircuitOpenDuration 열린 카카오 검색 회로가 외부 호출을 차단할 시간
 */
@ConfigurationProperties(prefix = "mydata.recovery")
public record MyDataRecoveryProperties(
        Duration retryDelay,
        int kakaoCircuitFailureThreshold,
        Duration kakaoCircuitOpenDuration
) {

    /**
     * 애플리케이션 시작 시 누락되거나 사용할 수 없는 복구 정책을 거부한다.
     */
    public MyDataRecoveryProperties {
        // 최초 실패와 재시도 사이에 회복 시간을 둘 수 있도록 양수만 허용한다.
        if (retryDelay == null || retryDelay.isZero() || retryDelay.isNegative()) {
            throw new IllegalArgumentException(
                    "mydata.recovery.retry-delay는 0보다 커야 합니다."
            );
        }

        // 연속 실패를 한 번 이상 확인한 뒤에만 회로를 열 수 있도록 양수로 제한한다.
        if (kakaoCircuitFailureThreshold < 1) {
            throw new IllegalArgumentException(
                    "mydata.recovery.kakao-circuit-failure-threshold는 1 이상이어야 합니다."
            );
        }

        // 열린 회로가 실제로 외부 호출을 차단할 수 있도록 양수만 허용한다.
        if (kakaoCircuitOpenDuration == null
                || kakaoCircuitOpenDuration.isZero()
                || kakaoCircuitOpenDuration.isNegative()) {
            throw new IllegalArgumentException(
                    "mydata.recovery.kakao-circuit-open-duration은 0보다 커야 합니다."
            );
        }
    }
}
