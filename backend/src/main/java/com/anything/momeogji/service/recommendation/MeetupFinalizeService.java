package com.anything.momeogji.service.recommendation;

import com.anything.momeogji.dto.recommendation.FinalNoticeResponse;
import com.anything.momeogji.dto.recommendation.FinalNoticeUpdateRequest;
import com.anything.momeogji.entity.recommendation.Meetup;

public interface MeetupFinalizeService {

    /** 호스트만 호출할 수 있다. 최신 회차의 득표수 기준으로 확정하며 동률이면 랜덤으로 정한다. */
    FinalNoticeResponse finalize(Long meetupId, Long callerId);

    /** 호스트만 호출할 수 있다. 확정된 약속시간을 수정하고 변경 이력을 남긴다. 값이 그대로면 아무 것도 하지 않는다. */
    FinalNoticeResponse updateFinalNotice(Long meetupId, FinalNoticeUpdateRequest request, Long callerId);

    /** 권한 검사 없이 확정을 수행한다. 전원 투표 완료 시 시스템이 자동으로 트리거할 때 사용한다. */
    FinalNoticeResponse finalizeInternal(Meetup meetup);

    /** 마감 시점에 표가 하나도 없으면 음식점 후보 중 한 곳을 무작위로 확정한다. */
    FinalNoticeResponse finalizeAfterDeadlineInternal(Meetup meetup);
}
