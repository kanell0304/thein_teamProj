package com.anything.momeogji.service.recommendation;

import com.anything.momeogji.dto.recommendation.VoteRequest;
import com.anything.momeogji.dto.recommendation.VoteTallyResult;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.stream.Collectors;

@Service
public class VotingServiceImpl implements VotingService {

    private final Random random;

    public VotingServiceImpl() {
        this(new SecureRandom());
    }

    // 테스트에서 결정적인 결과를 검증할 수 있도록 Random을 주입받는 생성자(패키지 전용)
    VotingServiceImpl(Random random) {
        this.random = random;
    }

    @Override
    public VoteTallyResult tally(List<VoteRequest> votes) {
        if (votes == null || votes.isEmpty()) {
            throw new IllegalArgumentException("집계할 투표 데이터가 없습니다.");
        }

        // 동일 참여자가 같은 음식점에 중복 제출해도 한 표로만 집계 (참여자별-후보별 유일성)
        Map<String, Long> voteCounts = votes.stream()
                .distinct()
                .collect(Collectors.groupingBy(
                        VoteRequest::restaurantName,
                        LinkedHashMap::new,
                        Collectors.counting()));

        long maxVotes = voteCounts.values().stream()
                .mapToLong(Long::longValue)
                .max()
                .orElseThrow();

        List<String> topCandidates = voteCounts.entrySet().stream()
                .filter(entry -> entry.getValue() == maxVotes)
                .map(Map.Entry::getKey)
                .toList();

        boolean tieBroken = topCandidates.size() > 1;
        String winner = tieBroken
                ? topCandidates.get(random.nextInt(topCandidates.size()))
                : topCandidates.get(0);

        return new VoteTallyResult(winner, tieBroken, voteCounts);
    }
}
