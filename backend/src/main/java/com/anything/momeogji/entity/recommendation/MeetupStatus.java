package com.anything.momeogji.entity.recommendation;

/** v3 기획서 "3. 전체 화면 흐름"의 상태 전이를 그대로 따른다. */
public enum MeetupStatus {
    DRAFT,
    PARTICIPANT_CONFIRMING,
    PREFERENCE_COLLECTING,
    RECOMMENDING,
    VOTING,
    FINALIZED,
    EXPIRED
}
