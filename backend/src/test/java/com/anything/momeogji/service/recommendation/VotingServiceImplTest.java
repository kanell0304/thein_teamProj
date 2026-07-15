package com.anything.momeogji.service.recommendation;

import com.anything.momeogji.dto.recommendation.VoteRequest;
import com.anything.momeogji.dto.recommendation.VoteTallyResult;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class VotingServiceImplTest {

    @Test
    void 최다득표_음식점을_그대로_1등으로_확정한다() {
        VotingServiceImpl votingService = new VotingServiceImpl(new Random(1));

        VoteTallyResult result = votingService.tally(List.of(
                new VoteRequest("u1", "A"),
                new VoteRequest("u2", "A"),
                new VoteRequest("u3", "B")
        ));

        assertThat(result.winnerRestaurantName()).isEqualTo("A");
        assertThat(result.tieBroken()).isFalse();
        assertThat(result.voteCounts()).containsEntry("A", 2L).containsEntry("B", 1L);
    }

    @Test
    void 공동_1등이면_주입된_난수로_결정적으로_확정한다() {
        Random alwaysSecond = new Random() {
            @Override
            public int nextInt(int bound) {
                return 1;
            }
        };
        VotingServiceImpl votingService = new VotingServiceImpl(alwaysSecond);

        VoteTallyResult result = votingService.tally(List.of(
                new VoteRequest("u1", "A"),
                new VoteRequest("u2", "B")
        ));

        assertThat(result.tieBroken()).isTrue();
        assertThat(result.winnerRestaurantName()).isEqualTo("B");
    }

    @Test
    void 같은_참여자의_같은_음식점_중복투표는_한_표로_집계한다() {
        VotingServiceImpl votingService = new VotingServiceImpl(new Random(1));

        VoteTallyResult result = votingService.tally(List.of(
                new VoteRequest("u1", "A"),
                new VoteRequest("u1", "A"),
                new VoteRequest("u2", "B")
        ));

        assertThat(result.voteCounts()).containsEntry("A", 1L).containsEntry("B", 1L);
    }

    @Test
    void 투표가_비어있으면_예외() {
        VotingServiceImpl votingService = new VotingServiceImpl(new Random());

        assertThatThrownBy(() -> votingService.tally(List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
