package com.anything.momeogji.service.recommendation;

import com.anything.momeogji.dto.recommendation.PreferenceSubmitRequest;
import com.anything.momeogji.dto.recommendation.PreferenceSubmitResponse;
import com.anything.momeogji.dto.recommendation.RoundResponse;

public interface MeetupPreferenceService {

    /**
     * 초대받은 참여자 본인의 개인 선호를 제출한다. 이 제출로 초대된 전원이 SUBMITTED가 되면
     * 즉시 AI 추천을 실행하고 결과를 응답/이벤트에 함께 담는다.
     */
    PreferenceSubmitResponse submitPreference(Long meetupId, Long callerId, PreferenceSubmitRequest request);

    /** 호스트만 호출할 수 있다. 아직 제출하지 않은 참여자가 있어도 지금까지 제출된 선호만으로 AI 추천을 강제 실행한다. */
    RoundResponse forceStartRecommendation(Long meetupId, Long callerId);
}
