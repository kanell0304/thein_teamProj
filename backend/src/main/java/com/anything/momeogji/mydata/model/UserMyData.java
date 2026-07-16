// 참가자 한 명의 카드별 정제 결과를 묶어 MyDataService가 반환하는 불변 데이터다.
package com.anything.momeogji.mydata.model;

import java.util.List;
import java.util.Objects;

/**
 * 동의한 참가자 한 명에 대해 수집·검증·파싱이 완료된 최종 마이데이터다.
 *
 * <p>이 모델에는 동의 여부, 개인 옵션, 공통 옵션 또는 비동기 처리 상태를 넣지 않는다.
 * 해당 정보는 옵션 결합·작업 상태 영역에서 별도로 관리한다.</p>
 *
 * <p>카드 또는 승인내역이 없는 것은 정상적인 결과이므로 {@code approvals}는
 * {@code null} 대신 빈 목록을 사용한다. Provider 또는 파싱이 실패한 경우에는
 * 이 객체를 빈 결과로 만들지 않고 MyDataService에서 처리 예외를 발생시켜야 한다.</p>
 *
 * @param participantId 모임 참가자를 식별하는 내부 ID
 * @param approvals 참가자의 모든 카드에서 수집한 정제 승인내역. 데이터가 없으면 빈 목록
 */
public record UserMyData(Long participantId, List<CardApprovalData> approvals) {
    /**
     * 참가자 ID와 목록의 불변성을 보장한다.
     * {@link List#copyOf(java.util.Collection)}를 사용하여 호출자가 전달한 목록을
     * 나중에 변경하더라도 이 결과가 함께 변경되지 않게 한다.
     */
    public UserMyData {
        if (participantId == null || participantId <= 0) {
            throw new IllegalArgumentException("participantId는 1 이상이어야 합니다.");
        }

        Objects.requireNonNull(approvals, "approvals는 null일 수 없습니다.");
        approvals = List.copyOf(approvals);
    }
}
