package com.anything.momeogji.mydata.transform;

import com.anything.momeogji.mydata.transform.local.MerchantPlaceSearchClient.SearchCandidate;
import org.springframework.stereotype.Component;

/**
 * 원본 가맹점명과 외부 장소 검색 후보의 이름을 비교해 매칭 신뢰도를 계산하는 컴포넌트
 *
 * 원본 문자열 완전일치, 비교용 이름 완전일치, 비교용 이름 부분일치 순서로 기본 점수를 결정한다.
 * 비교용 이름으로 다시 검색한 2차 결과에는 감점을 적용해 동일한 이름 일치 수준이라도 원본 검색 결과를 우선할 수 있게 한다.
 */
@Component
public class MerchantMatchScoreCalculator {

    private static final int RAW_NAME_EXACT_SCORE = 100;
    private static final int COMPARISON_NAME_EXACT_SCORE = 90;
    private static final int PARTIAL_MATCH_SCORE = 70;
    private static final int SECOND_ATTEMPT_PENALTY = 10;
    private static final double MIN_PARTIAL_LENGTH_RATIO = 0.60;

    private final MerchantNameParser merchantNameParser;

    /**
     * 외부 장소명에도 가맹점명과 동일한 비교용 문자열 생성 규칙을 적용하기 위해 Parser를 주입받는다.
     *
     * @param merchantNameParser 원본 문자열을 변경하지 않고 비교용 이름을 생성하는 컴포넌트
     */
    public MerchantMatchScoreCalculator(MerchantNameParser merchantNameParser) {
        this.merchantNameParser = merchantNameParser;
    }

    /**
     * 가맹점명과 외부 장소명을 비교하고 검색 회차를 반영한 최종 신뢰도 점수를 계산한다.
     *
     * @param merchantName 마이데이터의 원본 가맹점명
     * @param comparisonMerchantName 원본 가맹점명에서 생성한 비교용 문자열
     * @param candidate 외부 장소 검색 후보
     * @param attempt 검색 회차. 1차 또는 2차
     * @return 허용된 이름 매칭 점수. 일치하지 않으면 0
     */
    public int calculate(
            String merchantName,
            String comparisonMerchantName,
            SearchCandidate candidate,
            int attempt
    ) {
        String candidateComparisonName;

        try {
            // 외부 장소명도 원본 가맹점명과 같은 규칙으로 변환해 비교 기준을 일치시킨다.
            candidateComparisonName = merchantNameParser.createComparisonKey(
                    candidate.placeName()
            );
        } catch (IllegalArgumentException exception) {
            // 비교할 문자가 없는 외부 장소명은 유효한 매칭 후보로 사용하지 않는다.
            return 0;
        }

        int baseScore;

        // 원본 문자열의 문자 구성까지 완전히 같으면 가장 높은 신뢰도를 부여한다.
        if (merchantName.equals(candidate.placeName())) {
            baseScore = RAW_NAME_EXACT_SCORE;
        } else if (comparisonMerchantName.equals(candidateComparisonName)) {
            // NFKC·대소문자·공백 차이만 있는 이름은 두 번째 신뢰도 단계로 처리한다.
            baseScore = COMPARISON_NAME_EXACT_SCORE;
        } else if (isPartialMatch(comparisonMerchantName, candidateComparisonName)) {
            // 한 이름이 다른 이름을 충분한 비율로 포함하면 설명 가능한 부분일치로 인정한다.
            baseScore = PARTIAL_MATCH_SCORE;
        } else {
            // 세 가지 매칭 기준을 모두 충족하지 못하면 신뢰도 후보에서 제외한다.
            return 0;
        }

        // 비교용 이름으로 재검색한 결과에는 한 단계 낮은 신뢰도 감점을 적용한다.
        return attempt == 2
                ? baseScore - SECOND_ATTEMPT_PENALTY
                : baseScore;
    }

    /**
     * 두 비교용 이름 사이에 포함 관계가 있고 짧은 이름이 긴 이름의 60% 이상인지 확인한다.
     *
     * @param left 첫 번째 비교용 이름
     * @param right 두 번째 비교용 이름
     * @return 설명 가능한 포함 관계와 최소 길이 비율을 모두 충족하면 true
     */
    private boolean isPartialMatch(String left, String right) {
        // 완전일치는 상위 점수에서 처리했으므로 여기서는 포함 관계만 확인한다.
        if (!left.contains(right) && !right.contains(left)) {
            return false;
        }

        // 한글과 보조 문자를 포함한 이름 길이를 Unicode 코드 포인트 단위로 계산한다.
        int leftLength = left.codePointCount(0, left.length());
        int rightLength = right.codePointCount(0, right.length());
        int shorterLength = Math.min(leftLength, rightLength);
        int longerLength = Math.max(leftLength, rightLength);

        // 비교용 이름은 비어 있지 않으므로 0 나눗셈 없이 포함 길이 비율을 계산한다.
        double lengthRatio = (double) shorterLength / longerLength;
        return lengthRatio >= MIN_PARTIAL_LENGTH_RATIO;
    }
}
