package com.anything.momeogji.mydata.transform.model;

import java.util.List;

/**
 * 참가자 한 명의 마이데이터 수집·정제·집계·장소 분류가 끝난 최종 가공 결과다.
 *
 * <p>사용자 ID는 상위 옵션 처리 계층이 원래 사용자와 결과를 연결할 때 사용한다.
 * 옵션에서 전달된 선택 시각과 내부 시간대는 결제 필터링 후 폐기하며,
 * 가맹점 사용 이력에는 결제 로그와 목적 그룹 안에서 확인된 카카오 장소명·세부 카테고리만 담긴다.
 * 검색 실패·이름 미매칭·카테고리 누락 결과는 이 목록에 포함되지 않는다.</p>
 *
 * <p>이 모델은 참여자 간 집계나 AI 전달용 DTO가 아니다. 상위 계층은 AI에 전달하기 전에
 * 사용자 식별자와 개별 결제 발생 정보를 제거하고 그룹 단위 신호로 다시 가공해야 한다.</p>
 *
 * @param userId 마이데이터를 제공한 사용자를 식별하는 내부 ID
 * @param merchantUsages 원본 순서를 유지한 카카오 장소명·세부 카테고리 확인 완료 사용 이력 목록
 */
public record TransformedUserMyData(
        Long userId,
        List<MerchantUsageData> merchantUsages
) {

    /**
     * 최종 가공 결과의 사용자 식별값을 확인하고 사용 이력 목록을 불변 값으로 보존한다.
     */
    public TransformedUserMyData {
        // 결과를 원래 사용자와 연결할 수 있도록 사용자 ID가 양수인지 검증한다.
        if (userId == null || userId <= 0) {
            throw new IllegalArgumentException("userId는 1 이상이어야 합니다.");
        }

        // 정상적인 빈 결과는 허용하되 null 목록은 파이프라인 오류와 구분할 수 없으므로 거부한다.
        if (merchantUsages == null) {
            throw new IllegalArgumentException("merchantUsages는 null일 수 없습니다.");
        }

        // 호출자가 전달한 목록을 변경해도 최종 결과가 바뀌지 않도록 불변 목록으로 복사한다.
        merchantUsages = List.copyOf(merchantUsages);
    }
}
