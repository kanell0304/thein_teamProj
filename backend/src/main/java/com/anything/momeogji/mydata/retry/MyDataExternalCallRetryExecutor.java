package com.anything.momeogji.mydata.retry;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.function.Supplier;

/**
 * MyData 파이프라인의 외부 요청에서 일시적인 실패가 발생하면 같은 작업을 한 번만 다시 수행한다.
 *
 * <p>{@link RetryableMyDataExternalCallException}만 재시도 대상으로 취급한다. 최초 실패 후 설정된
 * 시간만큼 대기하고 두 번째 호출 결과를 그대로 반환하며, 두 번째 실패와 그 밖의 예외는 감추지 않는다.</p>
 */
@Component
public class MyDataExternalCallRetryExecutor {

    private static final Logger log = LoggerFactory.getLogger(MyDataExternalCallRetryExecutor.class);

    private final MyDataRecoveryProperties properties;

    /**
     * 외부 호출 재시도에 사용할 복구 정책을 주입받는다.
     *
     * @param properties 재시도 대기 시간과 회로 차단 설정
     */
    public MyDataExternalCallRetryExecutor(MyDataRecoveryProperties properties) {
        this.properties = properties;
    }

    /**
     * 외부 작업을 실행하고 재시도 가능한 최초 실패에 한해 같은 작업을 한 번 더 수행한다.
     *
     * @param operation 민감한 요청값을 포함하지 않은 외부 작업 이름
     * @param externalCall 실행할 외부 요청
     * @param <T> 외부 요청 결과 타입
     * @return 최초 호출 또는 한 번의 재시도로 얻은 결과
     * @throws IllegalArgumentException 작업 이름이나 실행 작업이 없는 경우
     * @throws IllegalStateException 재시도 대기 중 현재 스레드가 중단된 경우
     * @throws RetryableMyDataExternalCallException 재시도까지 같은 외부 실패가 발생한 경우
     */
    public <T> T execute(String operation, Supplier<T> externalCall) {
        // 로그와 예외 문맥에 사용할 수 있도록 공백 작업 이름을 거부한다.
        if (operation == null || operation.isBlank()) {
            throw new IllegalArgumentException("operation은 필수입니다.");
        }

        // 실제로 실행할 외부 작업이 없는 잘못된 호출을 즉시 거부한다.
        if (externalCall == null) {
            throw new IllegalArgumentException("externalCall은 필수입니다.");
        }

        try {
            // 최초 외부 요청을 실행하며 정상 결과와 재시도 대상이 아닌 예외는 그대로 반환·전파한다.
            return externalCall.get();
        } catch (RetryableMyDataExternalCallException firstFailure) {
            log.warn(
                    "MyData 외부 호출을 한 번 재시도합니다. operation={}, retryDelayMs={}, cause={}",
                    operation,
                    properties.retryDelay().toMillis(),
                    firstFailure.getMessage()
            );

            // 순간적인 외부 장애가 회복될 시간을 둔 뒤 동일 작업을 한 번만 다시 수행한다.
            waitBeforeRetry(operation);
            return externalCall.get();
        }
    }

    /**
     * 설정된 재시도 간격만큼 현재 요청 스레드를 대기시킨다.
     *
     * @param operation 중단 예외 문맥에 사용할 외부 작업 이름
     * @throws IllegalStateException 대기 중 인터럽트된 경우
     */
    private void waitBeforeRetry(String operation) {
        try {
            Thread.sleep(properties.retryDelay());
        } catch (InterruptedException exception) {
            // 상위 계층이 중단 상태를 감지할 수 있도록 인터럽트 플래그를 복원한다.
            Thread.currentThread().interrupt();
            throw new IllegalStateException(
                    operation + " 재시도 대기가 중단되었습니다.",
                    exception
            );
        }
    }
}
