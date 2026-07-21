package com.anything.momeogji.mydata.transform;

import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * 옵션에서 전달된 선택 시각과 카드 승인 시각을 비교하기 위한 MyData 내부 시간대다.
 *
 * <p>{@link #MORNING}은 02:00 이상 10:00 미만, {@link #LUNCH}는
 * 10:00 이상 16:00 미만, {@link #DINNER}는 16:00 이상 다음 날 02:00 미만을
 * 의미한다. 이 값은 결제 필터링에만 사용하며 최종 가공 모델에는 보존하지 않는다.</p>
 */
enum TimeBand {
    MORNING,
    LUNCH,
    DINNER;

    private static final int MORNING_START_HOUR = 2;
    private static final int LUNCH_START_HOUR = 10;
    private static final int DINNER_START_HOUR = 16;

    /**
     * 승인일시의 시각을 기준으로 해당 결제의 내부 시간대를 계산한다.
     *
     * @param approvedAt 분류할 카드 승인일시
     * @return 승인일시가 속한 아침·점심·저녁 시간대
     * @throws IllegalArgumentException 승인일시가 {@code null}인 경우
     */
    static TimeBand from(LocalDateTime approvedAt) {
        // 시간대를 계산할 승인일시가 존재하는지 검증한다.
        if (approvedAt == null) {
            throw new IllegalArgumentException("approvedAt은 null일 수 없습니다.");
        }

        // 날짜는 분류에 사용하지 않고 승인일시의 시각만 시간대 계산에 전달한다.
        return fromTime(approvedAt.toLocalTime());
    }

    /**
     * 옵션에서 받은 하루의 특정 시각을 내부 시간대 중 하나로 분류한다.
     *
     * @param time 분류할 시각
     * @return 시각이 속한 아침·점심·저녁 시간대
     * @throws IllegalArgumentException 시각이 {@code null}인 경우
     */
    static TimeBand fromTime(LocalTime time) {
        // 시간대를 계산할 시각이 존재하는지 검증한다.
        if (time == null) {
            throw new IllegalArgumentException("time은 null일 수 없습니다.");
        }

        int hour = time.getHour();

        // 02:00 이상 10:00 미만은 아침 시간대로 분류한다.
        if (hour >= MORNING_START_HOUR && hour < LUNCH_START_HOUR) {
            return MORNING;
        }

        // 10:00 이상 16:00 미만은 점심 시간대로 분류한다.
        if (hour >= LUNCH_START_HOUR && hour < DINNER_START_HOUR) {
            return LUNCH;
        }

        // 16:00 이상 또는 02:00 미만은 자정을 걸치는 저녁 시간대로 분류한다.
        return DINNER;
    }
}
