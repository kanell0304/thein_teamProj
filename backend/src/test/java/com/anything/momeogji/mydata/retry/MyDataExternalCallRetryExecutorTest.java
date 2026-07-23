package com.anything.momeogji.mydata.retry;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * MyData 외부 호출 실행기가 재시도 가능한 실패만 정확히 한 번 다시 수행하는지 검증한다.
 */
class MyDataExternalCallRetryExecutorTest {

    private final MyDataExternalCallRetryExecutor retryExecutor =
            new MyDataExternalCallRetryExecutor(new MyDataRecoveryProperties(
                    Duration.ofMillis(1),
                    3,
                    Duration.ofSeconds(1)
            ));

    /**
     * 최초 일시적 외부 실패 뒤 같은 작업이 성공하면 두 번째 결과를 반환하는지 확인한다.
     */
    @Test
    void 일시적_외부실패는_한번_재시도한다() {
        AtomicInteger attempts = new AtomicInteger();

        String result = retryExecutor.execute("테스트 외부 호출", () -> {
            if (attempts.incrementAndGet() == 1) {
                throw retryableFailure();
            }
            return "success";
        });

        assertThat(result).isEqualTo("success");
        assertThat(attempts).hasValue(2);
    }

    /**
     * 재시도까지 일시적 외부 실패가 반복되면 두 번째 예외를 전파하는지 확인한다.
     */
    @Test
    void 일시적_외부실패가_반복되면_두번째_예외를_전파한다() {
        AtomicInteger attempts = new AtomicInteger();

        assertThatThrownBy(() -> retryExecutor.execute("테스트 외부 호출", () -> {
            attempts.incrementAndGet();
            throw retryableFailure();
        })).isInstanceOf(RetryableMyDataExternalCallException.class);

        assertThat(attempts).hasValue(2);
    }

    /**
     * 결정적인 오류는 재시도하지 않고 최초 호출에서 바로 전달하는지 확인한다.
     */
    @Test
    void 일반_예외는_재시도하지_않는다() {
        AtomicInteger attempts = new AtomicInteger();

        assertThatThrownBy(() -> retryExecutor.execute("테스트 외부 호출", () -> {
            attempts.incrementAndGet();
            throw new IllegalStateException("결정적 오류");
        }))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("결정적 오류");

        assertThat(attempts).hasValue(1);
    }

    /**
     * 재시도 대기 중 인터럽트되면 두 번째 호출을 수행하지 않고 인터럽트 상태를 복원하는지 확인한다.
     */
    @Test
    void 재시도_대기가_중단되면_인터럽트상태를_복원한다() {
        AtomicInteger attempts = new AtomicInteger();
        Thread.currentThread().interrupt();

        try {
            assertThatThrownBy(() -> retryExecutor.execute("테스트 외부 호출", () -> {
                attempts.incrementAndGet();
                throw retryableFailure();
            }))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage("테스트 외부 호출 재시도 대기가 중단되었습니다.");

            assertThat(attempts).hasValue(1);
            assertThat(Thread.currentThread().isInterrupted()).isTrue();
        } finally {
            // 다음 테스트에 인터럽트 상태가 전달되지 않도록 현재 테스트가 설정한 플래그를 해제한다.
            Thread.interrupted();
        }
    }

    /**
     * 복구 설정이 재시도와 회로 차단에 사용할 수 있는 양수 값만 허용하는지 확인한다.
     */
    @Test
    void 잘못된_복구설정을_거부한다() {
        assertThatThrownBy(() -> new MyDataRecoveryProperties(
                Duration.ZERO,
                3,
                Duration.ofSeconds(1)
        )).isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> new MyDataRecoveryProperties(
                Duration.ofMillis(1),
                0,
                Duration.ofSeconds(1)
        )).isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> new MyDataRecoveryProperties(
                Duration.ofMillis(1),
                3,
                Duration.ZERO
        )).isInstanceOf(IllegalArgumentException.class);
    }

    /**
     * 테스트에서 반복 사용할 재시도 가능 외부 실패를 생성한다.
     *
     * @return 원본 외부 원인을 포함한 재시도 가능 예외
     */
    private RetryableMyDataExternalCallException retryableFailure() {
        return new RetryableMyDataExternalCallException(
                "일시적 외부 실패",
                new IllegalStateException("외부 장애")
        );
    }
}
