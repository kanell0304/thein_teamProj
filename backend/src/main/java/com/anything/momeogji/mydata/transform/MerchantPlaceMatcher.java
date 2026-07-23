package com.anything.momeogji.mydata.transform;

import com.anything.momeogji.mydata.transform.local.MerchantPlaceSearchClient;
import com.anything.momeogji.mydata.transform.local.MerchantPlaceSearchClient.SearchCandidate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 원본 가맹점명과 비교용 이름을 순차 검색해 이름 일치 우선순위가 가장 높은 장소 후보를 선정한다.
 *
 * <p>숫자형 신뢰도는 계산하거나 보존하지 않는다. 원본 문자열 완전일치, 비교용 이름 완전일치,
 * 부분일치 순서로 후보를 확인하고 원본 검색에서 강한 일치를 찾지 못했을 때만 비교용 이름으로
 * 한 번 더 검색한다. 같은 처리 호출에서 동일한 검색어가 반복되면 전달받은 캐시를 사용한다.</p>
 */
@Component
public class MerchantPlaceMatcher {

    private static final int MIN_PARTIAL_PERCENT = 60;
    private static final int PERCENT_BASE = 100;

    private final MerchantPlaceSearchClient merchantPlaceSearchClient;
    private final MerchantNameParser merchantNameParser;

    /**
     * 장소 검색과 비교용 이름 생성에 필요한 구성요소를 주입받는다.
     *
     * @param merchantPlaceSearchClient 가맹점명으로 외부 장소 후보를 조회하는 경계
     * @param merchantNameParser 원본과 외부 장소명에서 비교용 문자열을 생성하는 컴포넌트
     */
    public MerchantPlaceMatcher(
            MerchantPlaceSearchClient merchantPlaceSearchClient,
            MerchantNameParser merchantNameParser
    ) {
        this.merchantPlaceSearchClient = merchantPlaceSearchClient;
        this.merchantNameParser = merchantNameParser;
    }

    /**
     * 한 가맹점의 원본명과 비교용 이름을 최대 두 번 검색해 가장 우선하는 장소 후보들을 반환한다.
     *
     * <p>1차 검색의 원본명·비교용 이름 완전일치는 즉시 반환한다. 1차 검색에 부분일치만 있으면
     * 2차 검색의 완전일치를 먼저 확인하고, 2차에도 완전일치가 없으면 1차 부분일치를 우선한다.</p>
     *
     * @param merchantName 마이데이터의 원본 가맹점명. 미회신이면 null
     * @param categoryGroupCode 음식점 {@code FD6} 또는 카페 {@code CE7} 그룹 코드
     * @param searchCache 현재 분류 처리 호출 안에서 검색어별 결과를 재사용하는 캐시
     * @return 이름 일치 우선순위가 같은 장소 후보들을 포함한 불변 결과
     * @throws IllegalArgumentException 검색 캐시가 null인 경우
     */
    public MatchResult match(
            String merchantName,
            String categoryGroupCode,
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

        // 원본 가맹점명으로 1차 검색을 수행한다.
        List<SearchCandidate> firstSearchCandidates = searchWithCache(
                merchantName,
                categoryGroupCode,
                searchCache
        );

        // 원본 문자열 또는 비교용 이름이 완전히 일치하면 더 낮은 우선순위 후보를 확인하지 않는다.
        List<SearchCandidate> firstStrongMatches = findStrongMatches(
                merchantName,
                comparisonMerchantName,
                firstSearchCandidates
        );
        if (!firstStrongMatches.isEmpty()) {
            return MatchResult.matched(firstStrongMatches);
        }

        // 2차 검색보다 먼저 발견된 부분일치를 필요할 경우 다시 사용할 수 있도록 보존한다.
        List<SearchCandidate> firstPartialMatches = findPartialMatches(
                comparisonMerchantName,
                firstSearchCandidates
        );

        // 비교용 이름이 원본과 같으면 같은 검색을 반복하지 않고 1차 부분일치 결과로 종료한다.
        if (comparisonMerchantName.equals(merchantName)) {
            return MatchResult.fromCandidates(firstPartialMatches);
        }

        // 비교용 이름으로 2차 검색을 수행한다.
        List<SearchCandidate> secondSearchCandidates = searchWithCache(
                comparisonMerchantName,
                categoryGroupCode,
                searchCache
        );

        // 2차 검색에서 완전일치를 찾으면 1차 부분일치보다 우선한다.
        List<SearchCandidate> secondStrongMatches = findStrongMatches(
                merchantName,
                comparisonMerchantName,
                secondSearchCandidates
        );
        if (!secondStrongMatches.isEmpty()) {
            return MatchResult.matched(secondStrongMatches);
        }

        // 두 검색 모두 부분일치만 있다면 원본 검색 결과를 우선한다.
        if (!firstPartialMatches.isEmpty()) {
            return MatchResult.matched(firstPartialMatches);
        }

        // 1차 검색에서도 후보가 없었던 경우 2차 검색의 부분일치 여부로 최종 결과를 결정한다.
        return MatchResult.fromCandidates(
                findPartialMatches(comparisonMerchantName, secondSearchCandidates)
        );
    }

    /**
     * 카테고리가 충돌한 동일 우선순위 후보 중 가맹점명과 길이 비율이 유일하게 가장 높은 후보를 찾는다.
     *
     * <p>가맹점명과 장소명은 동일한 비교용 이름 규칙으로 변환한다. 유사도 값은 외부 모델에
     * 저장하지 않고 이 후보 선택 과정에서만 사용하며, 최고 비율 후보가 둘 이상이면 어느 후보도
     * 임의 선택하지 않는다.</p>
     *
     * @param merchantName 마이데이터의 원본 가맹점명
     * @param candidates 카테고리가 충돌한 동일 이름 일치 우선순위 후보 목록
     * @return 최고 길이 비율 후보가 하나면 해당 후보, 정확한 최고 비율 동률이면 빈 Optional
     */
    Optional<SearchCandidate> selectUniqueMostSimilarCandidate(
            String merchantName,
            List<SearchCandidate> candidates
    ) {
        String comparisonMerchantName = merchantNameParser.createComparisonKey(merchantName);
        SearchCandidate mostSimilarCandidate = null;
        NameSimilarityRatio highestSimilarity = null;
        boolean highestSimilarityTied = false;

        // 모든 후보의 정규화된 장소명 길이 비율을 현재 최고 비율과 비교한다.
        for (SearchCandidate candidate : candidates) {
            String comparisonPlaceName = createCandidateComparisonName(candidate);
            if (comparisonPlaceName == null) {
                continue;
            }

            NameSimilarityRatio candidateSimilarity = NameSimilarityRatio.of(
                    comparisonMerchantName,
                    comparisonPlaceName
            );

            // 첫 유효 후보 또는 기존 최고 비율보다 높은 후보를 새 최종 후보로 보존한다.
            if (highestSimilarity == null
                    || candidateSimilarity.compareTo(highestSimilarity) > 0) {
                mostSimilarCandidate = candidate;
                highestSimilarity = candidateSimilarity;
                highestSimilarityTied = false;
                continue;
            }

            // 교차 곱 결과가 정확히 같을 때만 최고 유사도 동률로 표시한다.
            if (candidateSimilarity.compareTo(highestSimilarity) == 0) {
                highestSimilarityTied = true;
            }
        }

        // 유일한 최고 후보가 없으면 카테고리 충돌을 해소하지 못한 것으로 처리한다.
        if (mostSimilarCandidate == null || highestSimilarityTied) {
            return Optional.empty();
        }
        return Optional.of(mostSimilarCandidate);
    }

    /**
     * 같은 처리 호출에서 이미 조회한 검색어는 외부 API를 다시 호출하지 않고 이전 결과를 재사용한다.
     *
     * @param query 원본 또는 비교용 가맹점명 검색어
     * @param categoryGroupCode 현재 처리 호출에서 고정된 음식점 또는 카페 그룹 코드
     * @param searchCache 검색어별 불변 후보 목록 캐시
     * @return 외부 제공자 응답 순서를 유지한 검색 후보 목록
     */
    private List<SearchCandidate> searchWithCache(
            String query,
            String categoryGroupCode,
            Map<String, List<SearchCandidate>> searchCache
    ) {
        // 외부 클라이언트 결과를 방어적으로 복사해 캐시 이후 변경되지 않게 한다.
        return searchCache.computeIfAbsent(
                query,
                ignored -> List.copyOf(
                        merchantPlaceSearchClient.search(query, categoryGroupCode)
                )
        );
    }

    /**
     * 원본 문자열 완전일치를 먼저 찾고, 없으면 비교용 이름 완전일치를 찾는다.
     *
     * @param merchantName 마이데이터의 원본 가맹점명
     * @param comparisonMerchantName 원본 가맹점명의 비교용 문자열
     * @param candidates 현재 검색 회차에서 받은 장소 후보
     * @return 가장 우선하는 완전일치 후보 목록
     */
    private List<SearchCandidate> findStrongMatches(
            String merchantName,
            String comparisonMerchantName,
            List<SearchCandidate> candidates
    ) {
        // 원본 문자열이 완전히 같은 후보를 가장 먼저 선택한다.
        List<SearchCandidate> rawNameMatches = candidates.stream()
                .filter(candidate -> merchantName.equals(candidate.placeName()))
                .toList();
        if (!rawNameMatches.isEmpty()) {
            return rawNameMatches;
        }

        // 문자 정규화·대소문자·공백 차이만 있는 후보를 두 번째 우선순위로 선택한다.
        return candidates.stream()
                .filter(candidate -> comparisonMerchantName.equals(
                        createCandidateComparisonName(candidate)
                ))
                .toList();
    }

    /**
     * 비교용 이름 사이에 충분한 포함 관계가 있는 장소 후보를 찾는다.
     *
     * @param comparisonMerchantName 원본 가맹점명의 비교용 문자열
     * @param candidates 현재 검색 회차에서 받은 장소 후보
     * @return 외부 응답 순서를 유지한 부분일치 후보 목록
     */
    private List<SearchCandidate> findPartialMatches(
            String comparisonMerchantName,
            List<SearchCandidate> candidates
    ) {
        return candidates.stream()
                .filter(candidate -> isPartialMatch(
                        comparisonMerchantName,
                        createCandidateComparisonName(candidate)
                ))
                .toList();
    }

    /**
     * 외부 장소명을 가맹점명과 같은 비교 규칙으로 변환한다.
     *
     * @param candidate 카카오 장소 검색 후보
     * @return 비교용 장소명. 비교할 문자가 없으면 null
     */
    private String createCandidateComparisonName(SearchCandidate candidate) {
        try {
            return merchantNameParser.createComparisonKey(candidate.placeName());
        } catch (IllegalArgumentException exception) {
            // 비교할 문자가 없는 외부 장소명은 이름 매칭 대상에서 제외한다.
            return null;
        }
    }

    /**
     * 두 비교용 이름 사이에 포함 관계가 있고 짧은 이름이 긴 이름의 60% 이상인지 확인한다.
     *
     * @param left 원본 가맹점명의 비교용 문자열
     * @param right 외부 장소명의 비교용 문자열
     * @return 설명 가능한 포함 관계와 최소 길이 비율을 모두 충족하면 true
     */
    private boolean isPartialMatch(String left, String right) {
        // 비교용 외부 장소명을 만들 수 없으면 부분일치 후보로 사용하지 않는다.
        if (right == null) {
            return false;
        }

        // 완전일치는 상위 우선순위에서 처리했으므로 여기서는 서로 다른 이름의 포함 관계만 확인한다.
        if (left.equals(right) || (!left.contains(right) && !right.contains(left))) {
            return false;
        }

        NameSimilarityRatio similarityRatio = NameSimilarityRatio.of(left, right);

        // 부동소수점 없이 짧은 이름이 긴 이름의 60% 이상인지 교차 곱으로 확인한다.
        return similarityRatio.meetsMinimumPercent(MIN_PARTIAL_PERCENT);
    }

    /**
     * 두 정규화 이름의 짧은 길이와 긴 길이를 유리수 형태로 보존하는 내부 비교 값이다.
     *
     * <p>나눗셈이나 반올림 없이 교차 곱으로 크기와 동률을 비교해 부동소수점 오차를 피한다.</p>
     *
     * @param shorterLength 두 이름 중 짧은 Unicode 코드 포인트 길이
     * @param longerLength 두 이름 중 긴 Unicode 코드 포인트 길이
     */
    private record NameSimilarityRatio(
            int shorterLength,
            int longerLength
    ) implements Comparable<NameSimilarityRatio> {

        /**
         * 두 비교용 이름에서 Unicode 코드 포인트 기준 길이 비율을 생성한다.
         *
         * @param left 첫 번째 비교용 이름
         * @param right 두 번째 비교용 이름
         * @return 짧은 길이와 긴 길이를 보존한 유사도 비율
         */
        private static NameSimilarityRatio of(String left, String right) {
            int leftLength = left.codePointCount(0, left.length());
            int rightLength = right.codePointCount(0, right.length());
            return new NameSimilarityRatio(
                    Math.min(leftLength, rightLength),
                    Math.max(leftLength, rightLength)
            );
        }

        /**
         * 현재 이름 길이 비율이 지정한 최소 백분율 이상인지 나눗셈 없이 확인한다.
         *
         * @param minimumPercent 허용할 최소 백분율
         * @return 짧은 길이 비율이 최소 백분율 이상이면 true
         */
        private boolean meetsMinimumPercent(int minimumPercent) {
            return (long) shorterLength * PERCENT_BASE
                    >= (long) longerLength * minimumPercent;
        }

        /**
         * 두 길이 비율을 교차 곱해 어느 값이 더 큰지 또는 정확히 같은지 비교한다.
         *
         * @param other 비교할 다른 이름 길이 비율
         * @return 현재 비율이 작으면 음수, 같으면 0, 크면 양수
         */
        @Override
        public int compareTo(NameSimilarityRatio other) {
            long currentCrossProduct = (long) shorterLength * other.longerLength;
            long otherCrossProduct = (long) other.shorterLength * longerLength;
            return Long.compare(currentCrossProduct, otherCrossProduct);
        }
    }

    /**
     * 같은 이름 일치 우선순위를 가진 장소 후보들을 후속 카테고리 합의 단계에 전달하는 결과다.
     *
     * @param bestCandidates 외부 제공자 응답 순서를 유지한 최종 장소 후보 목록
     */
    public record MatchResult(List<SearchCandidate> bestCandidates) {

        /**
         * 후보 목록을 null이 아닌 불변 값으로 보존한다.
         */
        public MatchResult {
            // 후보 목록은 null을 허용하지 않고 외부에서 변경할 수 없는 값으로 복사한다.
            if (bestCandidates == null) {
                throw new IllegalArgumentException("bestCandidates는 null일 수 없습니다.");
            }
            bestCandidates = List.copyOf(bestCandidates);
        }

        /**
         * 검색할 이름이 없거나 허용 가능한 장소 후보를 찾지 못한 결과를 생성한다.
         *
         * @return 빈 후보 목록을 가진 미매칭 결과
         */
        private static MatchResult unmatched() {
            return new MatchResult(List.of());
        }

        /**
         * 하나 이상의 일치 후보를 가진 결과를 생성한다.
         *
         * @param candidates 최종 선택한 장소 후보 목록
         * @return 전달받은 후보를 불변으로 보존한 매칭 결과
         */
        private static MatchResult matched(List<SearchCandidate> candidates) {
            return new MatchResult(candidates);
        }

        /**
         * 후보 목록의 존재 여부에 따라 매칭 또는 미매칭 결과를 생성한다.
         *
         * @param candidates 최종 이름 비교가 끝난 장소 후보 목록
         * @return 후보가 있으면 매칭 결과, 없으면 미매칭 결과
         */
        private static MatchResult fromCandidates(List<SearchCandidate> candidates) {
            return candidates.isEmpty() ? unmatched() : matched(candidates);
        }

        /**
         * 외부 장소명과 일치하는 후보를 하나 이상 찾았는지 확인한다.
         *
         * @return 최고 후보 목록이 비어 있지 않으면 true
         */
        public boolean matched() {
            return !bestCandidates.isEmpty();
        }
    }
}
