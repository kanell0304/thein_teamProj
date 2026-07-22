package com.anything.momeogji.service.recommendation;

/** MydataConsent.processedResult에 저장되는 참여자의 음식점(FD6)/카페(CE7) 방문 횟수 요약. */
record CategoryUsageSummary(long fd6Count, long ce7Count) {
}
