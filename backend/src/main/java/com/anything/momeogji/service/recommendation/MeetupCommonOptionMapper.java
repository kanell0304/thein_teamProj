package com.anything.momeogji.service.recommendation;

import com.anything.momeogji.dto.recommendation.CommonOptionRequest;
import com.anything.momeogji.entity.recommendation.Meetup;

/** Meetup 엔티티에 저장된 공통 옵션 컬럼을 CommonOptionRequest로 되돌린다(BigDecimal<->Double 변환 포함). */
final class MeetupCommonOptionMapper {

    private MeetupCommonOptionMapper() {
    }

    static CommonOptionRequest toCommonOption(Meetup meetup) {
        return new CommonOptionRequest(
                meetup.getDestinationName(),
                meetup.getDestinationLatitude().doubleValue(),
                meetup.getDestinationLongitude().doubleValue(),
                meetup.getMeetingTime(),
                meetup.getPurpose()
        );
    }
}
