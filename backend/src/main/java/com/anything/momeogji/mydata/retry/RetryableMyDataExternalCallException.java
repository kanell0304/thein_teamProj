package com.anything.momeogji.mydata.retry;

/**
 * 연결 실패, 타임아웃, HTTP 429·5xx처럼 같은 외부 요청을 한 번 더 수행할 가치가 있는
 * 일시적 MyData 외부 호출 실패를 표현한다.
 *
 * <p>잘못된 입력, Dummy 파일 오류, JSON 역직렬화·검증·파싱 오류는 이 예외로 변환하지 않는다.
 * 따라서 {@link MyDataExternalCallRetryExecutor}는 외부 환경이 달라지면 회복될 수 있는 실패만
 * 재시도하고 결정적인 데이터 오류는 즉시 호출자에게 전달한다.</p>
 */
public class RetryableMyDataExternalCallException extends RuntimeException {

    /**
     * 외부 호출의 일시적 실패 원인을 보존한 재시도 가능 예외를 생성한다.
     *
     * @param message 민감한 요청·응답 데이터를 포함하지 않은 실패 설명
     * @param cause 외부 Client가 전달한 원본 예외
     */
    public RetryableMyDataExternalCallException(String message, Throwable cause) {
        super(message, cause);
    }
}
