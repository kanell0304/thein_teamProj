package com.anything.momeogji.service.recommendation;

import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 진행 중인 투표의 마감 시각을 서버에서 확인해 모든 접속자에게 같은 결과를 제공한다. */
@Component
@RequiredArgsConstructor
public class MeetupVoteDeadlineScheduler {

    private final MeetupVoteService meetupVoteService;

    @Scheduled(fixedDelayString = "${momeokji.vote-expiration-check-ms:1000}")
    public void resolveExpiredVotes() {
        meetupVoteService.resolveExpiredVotes();
    }
}
