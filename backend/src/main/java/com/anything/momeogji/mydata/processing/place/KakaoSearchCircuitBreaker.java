package com.anything.momeogji.mydata.processing.place;

import com.anything.momeogji.mydata.retry.MyDataRecoveryProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 카카오 키워드 검색의 연속 최종 외부 실패가 누적되면 일정 시간 캐시 MISS 요청을 차단한다.
 *
 * <p>회로 상태는 현재 JVM에서만 공유된다. 정상 외부 응답은 연속 실패 횟수를 초기화하며,
 * 열린 회로의 유지 시간이 지나면 다음 외부 요청을 다시 허용한다.</p>
 */
@Component
public class KakaoSearchCircuitBreaker {

    private static final Logger log = LoggerFactory.getLogger(KakaoSearchCircuitBreaker.class);

    private final MyDataRecoveryProperties properties;
    private final AtomicInteger consecutiveFinalFailures = new AtomicInteger();
    private final AtomicLong circuitOpenUntilNanos = new AtomicLong();

    /**
     * 회로를 열 연속 실패 횟수와 차단 시간을 포함한 복구 정책을 주입받는다.
     *
     * @param properties MyData 외부 호출 복구 설정
     */
    public KakaoSearchCircuitBreaker(MyDataRecoveryProperties properties) {
        this.properties = properties;
    }

    /**
     * 현재 캐시 MISS가 카카오 외부 요청을 수행해도 되는 상태인지 확인한다.
     *
     * @return 회로가 닫혀 있거나 기존 OPEN 시간이 만료됐으면 true
     */
    public boolean allowRequest() {
        long openUntilNanos = circuitOpenUntilNanos.get();
        if (openUntilNanos == 0L) {
            return true;
        }

        long now = System.nanoTime();
        if (now < openUntilNanos) {
            return false;
        }

        // OPEN 시간이 지난 첫 확인자가 회로와 연속 실패 횟수를 초기화한다.
        if (circuitOpenUntilNanos.compareAndSet(openUntilNanos, 0L)) {
            consecutiveFinalFailures.set(0);
        }
        return circuitOpenUntilNanos.get() == 0L;
    }

    /**
     * 정상적인 카카오 외부 응답을 받았을 때 연속 실패 상태를 초기화한다.
     *
     * <p>장소 배열이 비어 있어도 정상 HTTP 응답과 유효한 응답 구조라면 성공으로 기록한다.</p>
     */
    public void recordSuccess() {
        consecutiveFinalFailures.set(0);
        circuitOpenUntilNanos.set(0L);
    }

    /**
     * 한 외부 요청이 1회 재시도 후에도 실패했음을 기록하고 임계값에 도달하면 회로를 연다.
     */
    public void recordFinalFailure() {
        int failureCount = consecutiveFinalFailures.incrementAndGet();
        if (failureCount < properties.kakaoCircuitFailureThreshold()) {
            return;
        }

        long openDurationNanos = properties.kakaoCircuitOpenDuration().toNanos();
        long openUntilNanos = System.nanoTime() + openDurationNanos;
        circuitOpenUntilNanos.set(openUntilNanos);

        log.warn(
                "카카오 MyData 검색 회로를 엽니다. consecutiveFailures={}, openDurationSeconds={}",
                failureCount,
                properties.kakaoCircuitOpenDuration().toSeconds()
        );
    }
}
