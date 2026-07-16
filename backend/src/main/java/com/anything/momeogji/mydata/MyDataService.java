package com.anything.momeogji.mydata;

import com.anything.momeogji.mydata.model.ParticipantMyData;

/**
 * 마이데이터 수집 전체 흐름의 진입점과 반환 계약을 정의
 * 옵션 선택과정에서 사용 동의를 한 경우에만 진입한다.
 * 이 메서드는 동기식 핵심 처리 계약을 유지하고, 이후 참가자 단위 비동기 실행 계층이 이 서비스를 호출하도록 구성
 *
 * <p>구현체는 수집 시작 시 {@code LocalDate.now()}를 한 번 호출해 종료일로 저장하고,
 * 그 날짜에서 1년을 뺀 값을 시작일로 정해야 한다. 같은 수집 작업에 포함된 모든
 * 카드와 페이지 요청에는 처음 계산한 동일한 기간을 전달한다.</p>
 */
public interface MyDataService {
    /**
     * 지정한 참가자의 카드 마이데이터를 수집하고 하나의 결과로 반환한다.
     *
     * <p>정상적으로 조회했지만 카드 또는 승인내역이 없는 경우에도 {@code null}을
     * 반환하지 않는다. 해당 참가자 ID와 빈 승인내역 목록을 가진
     * {@link ParticipantMyData}를 정상 결과로 반환해야 한다.</p>
     *
     * <p>Provider 호출, JSON 파싱 또는 응답 검증이 실패한 경우에는 빈 결과로
     * 위장하지 않고 명시적인 처리 예외를 발생시켜야 한다. 호출 영역은 실패 상태를
     * 기록한 뒤 해당 참가자의 개인 옵션만 사용하여 AI 입력을 계속 구성한다.</p>
     *
     * @param participantId 모임 참가자를 식별하는 내부 ID. {@code null} 또는 0 이하는 허용하지 않는다.
     * @return 참가자 ID와 정제된 카드 승인내역을 포함하는 불변 결과
     * @throws IllegalArgumentException 참가자 ID가 없거나 0 이하인 경우
     */
    ParticipantMyData collect(Long participantId);
}
