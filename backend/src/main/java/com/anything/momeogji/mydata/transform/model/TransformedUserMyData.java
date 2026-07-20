package com.anything.momeogji.mydata.transform.model;

import java.util.List;

/**
 * 참가자 한 명의 마이데이터 수집·정제·집계·장소 분류가 끝난 최종 가공 결과다.
 *
 * <p>참가자 ID는 상위 옵션 처리 계층이 원래 참가자와 결과를 연결할 때 사용하고,
 * 선택 시간대는 결과가 비어 있어도 어떤 조건으로 가공했는지 보존한다.
 * 가맹점 사용 이력에는 결제 로그와 카카오 장소 매칭 결과가 함께 담긴다.</p>
 *
 * <p>이 모델은 참여자 간 집계나 AI 전달용 DTO가 아니다. 상위 계층은 AI에 전달하기 전에
 * 참가자 식별자와 개별 결제 발생 정보를 제거하고 그룹 단위 신호로 다시 가공해야 한다.</p>
 *
 * @param participantId 모임 참가자를 식별하는 내부 ID
 * @param selectedTimeBand 가맹점 사용 이력을 추출할 때 적용한 시간대
 * @param merchantUsages 원본 순서를 유지하고 카카오 장소 결과를 포함한 가맹점 사용 이력 목록
 */
public record TransformedUserMyData(
        Long participantId,
        TimeBand selectedTimeBand,
        List<MerchantUsageData> merchantUsages
) {

    /**
     * 최종 가공 결과의 식별값과 시간대가 존재하는지 확인하고 사용 이력 목록을 불변 값으로 보존한다.
     */
    public TransformedUserMyData {
        // 결과를 원래 참가자와 연결할 수 있도록 참가자 ID가 양수인지 검증한다.
        if (participantId == null || participantId <= 0) {
            throw new IllegalArgumentException("participantId는 1 이상이어야 합니다.");
        }

        // 결과가 비어 있어도 어떤 시간대 조건으로 처리했는지 알 수 있도록 시간대를 필수로 검증한다.
        if (selectedTimeBand == null) {
            throw new IllegalArgumentException("selectedTimeBand는 필수입니다.");
        }

        // 정상적인 빈 결과는 허용하되 null 목록은 파이프라인 오류와 구분할 수 없으므로 거부한다.
        if (merchantUsages == null) {
            throw new IllegalArgumentException("merchantUsages는 null일 수 없습니다.");
        }

        // 호출자가 전달한 목록을 변경해도 최종 결과가 바뀌지 않도록 불변 목록으로 복사한다.
        merchantUsages = List.copyOf(merchantUsages);
    }
}
