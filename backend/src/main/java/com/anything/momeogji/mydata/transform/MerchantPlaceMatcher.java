package com.anything.momeogji.mydata.transform;

import com.anything.momeogji.mydata.transform.local.MerchantPlaceSearchClient;
import com.anything.momeogji.mydata.transform.local.MerchantPlaceSearchClient.SearchCandidate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 원본 가맹점명과 비교용 이름을 순차 검색하고 가장 높은 이름 매칭 점수의 장소 후보를 선정
 *
 * 원본명 검색에서 충분한 점수의 후보를 찾지 못한 경우에만 비교용 이름으로 다시 검색한다.
 * 같은 처리 호출에서 동일한 검색어가 반복되면 전달받은 캐시를 사용하며, 검색 회차와 외부 제공자의 응답 순서를 그대로 보존한 최고 점수 후보 목록을 반환한다.
 */
@Component
public class MerchantPlaceMatcher {

    private static final int SECOND_SEARCH_THRESHOLD = 90;

    private final MerchantPlaceSearchClient merchantPlaceSearchClient;
    private final MerchantNameParser merchantNameParser;
    private final MerchantMatchScoreCalculator matchScoreCalculator;

    /**
     * 장소 검색, 비교용 이름 생성과 이름 매칭 점수 계산에 필요한 구성요소를 주입받는다.
     *
     * @param merchantPlaceSearchClient 가맹점명으로 외부 장소 후보를 조회하는 경계
     * @param merchantNameParser 원본 가맹점명에서 비교용 문자열을 생성하는 컴포넌트
     * @param matchScoreCalculator 가맹점명과 외부 장소명의 매칭 신뢰도를 계산하는 컴포넌트
     */
    public MerchantPlaceMatcher(
            MerchantPlaceSearchClient merchantPlaceSearchClient,
            MerchantNameParser merchantNameParser,
            MerchantMatchScoreCalculator matchScoreCalculator
    ) {
        this.merchantPlaceSearchClient = merchantPlaceSearchClient;
        this.merchantNameParser = merchantNameParser;
        this.matchScoreCalculator = matchScoreCalculator;
    }

    /**
     * 한 가맹점의 원본명과 비교용 이름을 최대 두 번 검색해 최고 신뢰도 장소 후보를 반환한다.
     *
     * <p>가맹점명이 없거나 두 검색에서 이름이 부분 일치하는 후보조차 찾지 못하면
     * 신뢰도 0과 빈 후보 목록을 가진 미매칭 결과를 반환한다.</p>
     *
     * @param merchantName 마이데이터의 원본 가맹점명. 미회신이면 null
     * @param searchCache 현재 분류 처리 호출 안에서 검색어별 결과를 재사용하는 캐시
     * @return 최고 신뢰도와 동일 점수 장소 후보들을 포함한 불변 매칭 결과
     * @throws IllegalArgumentException 검색 캐시가 null인 경우
     */
    public MatchResult match(
            String merchantName,
            Map<String, List<SearchCandidate>> searchCache
    ) {
        // 한 번의 처리 범위에서 검색 결과를 재사용할 캐시가 존재하는지 검증한다.
        if (searchCache == null) {
            throw new IllegalArgumentException("searchCache는 null일 수 없습니다.");
        }

        // 가맹점명이 미회신된 경우 외부 검색을 수행하지 않고 미매칭 결과를 반환한다.
        if (merchantName == null) {
            return MatchResult.unmatched();
        }

        // API 응답 비교와 2차 검색에 사용할 비교용 가맹점명을 생성한다.
        String comparisonMerchantName = merchantNameParser.createComparisonKey(merchantName);

        List<ScoredCandidate> scoredCandidates = new ArrayList<>();

        // 원본 가맹점명으로 1차 검색하고 각 후보의 이름 매칭 점수를 계산한다.
        addScoredCandidates(
                scoredCandidates,
                merchantName,
                comparisonMerchantName,
                searchWithCache(merchantName, searchCache),
                1
        );

        int firstAttemptBestScore = findBestScore(scoredCandidates);

        // 1차 검색에 90점 이상 후보가 없고 검색어가 실제로 달라질 때만 비교용 이름으로 재검색한다.
        if (firstAttemptBestScore < SECOND_SEARCH_THRESHOLD
                && !comparisonMerchantName.equals(merchantName)) {
            addScoredCandidates(
                    scoredCandidates,
                    merchantName,
                    comparisonMerchantName,
                    searchWithCache(comparisonMerchantName, searchCache),
                    2
            );
        }

        // 전체 검색 회차에서 가장 높은 이름 매칭 점수를 확인한다.
        int bestScore = findBestScore(scoredCandidates);
        if (bestScore == 0) {
            return MatchResult.unmatched();
        }

        // 검색 회차와 외부 제공자 응답 순서를 유지하면서 최고 점수 후보만 반환한다.
        List<SearchCandidate> bestCandidates = scoredCandidates.stream()
                .filter(candidate -> candidate.score() == bestScore)
                .map(ScoredCandidate::candidate)
                .toList();

        return new MatchResult(bestCandidates, bestScore);
    }

    /**
     * 같은 처리 호출에서 이미 조회한 검색어는 외부 API를 다시 호출하지 않고 이전 결과를 재사용한다.
     *
     * @param query 원본 또는 비교용 가맹점명 검색어
     * @param searchCache 검색어별 불변 후보 목록 캐시
     * @return 외부 제공자 응답 순서를 유지한 검색 후보 목록
     */
    private List<SearchCandidate> searchWithCache(
            String query,
            Map<String, List<SearchCandidate>> searchCache
    ) {
        // 외부 클라이언트 결과를 방어적으로 복사해 캐시 이후 변경되지 않게 한다.
        return searchCache.computeIfAbsent(
                query,
                ignored -> List.copyOf(merchantPlaceSearchClient.search(query))
        );
    }

    /**
     * 한 검색 회차의 모든 장소 후보를 비교하고 허용 점수를 받은 후보만 누적한다.
     *
     * @param destination 전체 검색 회차의 점수 후보를 누적할 목록
     * @param merchantName 마이데이터의 원본 가맹점명
     * @param comparisonMerchantName 원본 가맹점명의 비교용 문자열
     * @param candidates 현재 검색 회차에서 받은 장소 후보
     * @param attempt 검색 회차. 1차 또는 2차
     */
    private void addScoredCandidates(
            List<ScoredCandidate> destination,
            String merchantName,
            String comparisonMerchantName,
            List<SearchCandidate> candidates,
            int attempt
    ) {
        // 외부 제공자 응답 순서대로 후보를 평가해 같은 점수일 때의 우선순위를 보존한다.
        for (SearchCandidate candidate : candidates) {
            int score = matchScoreCalculator.calculate(
                    merchantName,
                    comparisonMerchantName,
                    candidate,
                    attempt
            );

            // 이름 매칭 기준을 충족하지 못한 장소는 최고 후보 선정 대상에서 제외한다.
            if (score == 0) {
                continue;
            }

            // 검색 회차와 외부 제공자 응답 순서를 유지한 상태로 점수 후보를 누적한다.
            destination.add(new ScoredCandidate(candidate, score));
        }
    }

    /**
     * 현재까지 누적된 점수 후보 중 가장 높은 점수를 찾는다.
     *
     * @param scoredCandidates 검색 회차 순서대로 누적된 점수 후보 목록
     * @return 최고 점수. 후보가 없으면 0
     */
    private int findBestScore(List<ScoredCandidate> scoredCandidates) {
        int bestScore = 0;

        // 모든 후보의 점수를 비교해 외부 API 정렬 순서와 무관한 최고 점수를 계산한다.
        for (ScoredCandidate candidate : scoredCandidates) {
            bestScore = Math.max(bestScore, candidate.score());
        }
        return bestScore;
    }

    /**
     * 최고 이름 매칭 신뢰도와 같은 점수를 받은 장소 후보들을 함께 전달하는 내부 결과다.
     *
     * @param bestCandidates 검색 회차와 외부 제공자 응답 순서를 유지한 최고 점수 후보 목록
     * @param matchConfidence 최고 이름 매칭 신뢰도. 미매칭이면 0
     */
    public record MatchResult(
            List<SearchCandidate> bestCandidates,
            int matchConfidence
    ) {

        /**
         * 최고 후보 목록과 신뢰도 조합이 후속 분류에서 안전하게 사용될 수 있는지 검증한다.
         */
        public MatchResult {
            // 후보 목록은 null을 허용하지 않고 외부에서 변경할 수 없는 값으로 복사한다.
            if (bestCandidates == null) {
                throw new IllegalArgumentException("bestCandidates는 null일 수 없습니다.");
            }
            bestCandidates = List.copyOf(bestCandidates);

            // 신뢰도는 최종 분류 모델과 같은 백분율 범위를 사용한다.
            if (matchConfidence < 0 || matchConfidence > 100) {
                throw new IllegalArgumentException(
                        "matchConfidence는 0 이상 100 이하여야 합니다."
                );
            }

            // 미매칭과 매칭 상태가 최고 후보 목록의 존재 여부와 일치하는지 검증한다.
            if (matchConfidence == 0 && !bestCandidates.isEmpty()) {
                throw new IllegalArgumentException(
                        "matchConfidence가 0이면 최고 후보가 없어야 합니다."
                );
            }
            if (matchConfidence > 0 && bestCandidates.isEmpty()) {
                throw new IllegalArgumentException(
                        "matchConfidence가 0보다 크면 최고 후보가 필요합니다."
                );
            }
        }

        /**
         * 검색할 이름이 없거나 허용 가능한 장소 후보를 찾지 못한 결과를 생성한다.
         *
         * @return 빈 후보 목록과 신뢰도 0을 가진 미매칭 결과
         */
        private static MatchResult unmatched() {
            return new MatchResult(List.of(), 0);
        }

        /**
         * 외부 장소명과 일치하는 후보를 하나 이상 찾았는지 확인한다.
         *
         * @return 최고 후보가 있고 신뢰도가 0보다 크면 true
         */
        public boolean matched() {
            return matchConfidence > 0;
        }
    }

    /**
     * 외부 장소 검색 후보와 계산된 이름 매칭 점수를 함께 보존하는 내부 값이다.
     *
     * @param candidate 외부 장소 검색 후보
     * @param score 검색 회차 감점까지 적용한 최종 이름 매칭 점수
     */
    private record ScoredCandidate(
            SearchCandidate candidate,
            int score
    ) {
    }
}
