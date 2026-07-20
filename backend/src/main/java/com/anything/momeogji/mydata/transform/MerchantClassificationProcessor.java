package com.anything.momeogji.mydata.transform;

import com.anything.momeogji.mydata.transform.MerchantPlaceMatcher.MatchResult;
import com.anything.momeogji.mydata.transform.local.MerchantPlaceSearchClient.SearchCandidate;
import com.anything.momeogji.mydata.transform.model.KakaoPlaceMatchData;
import com.anything.momeogji.mydata.transform.model.MerchantUsageData;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 가맹점별 결제 사용 이력에 카카오 로컬 장소 매칭 결과를 결합한다.
 *
 * <p>장소 검색과 이름 신뢰도 계산은 {@link MerchantPlaceMatcher}에 위임하고,
 * 최고 후보들의 카테고리와 좌표를 해석해 기존 {@link MerchantUsageData}의
 * {@code kakaoPlaceMatch}만 교체한 새 불변 값을 만든다.</p>
 *
 * <p>검색 실패와 미매칭 가맹점은 집계 단계에서 만든 미매칭 기본값을 유지한다.
 * 음식점·카페가 아닌 명시적 카카오 카테고리는 최종 목록에서 제외하며,
 * 입력 가맹점의 원래 순서를 보존한 불변 목록을 반환한다.</p>
 */
@Component
public class MerchantClassificationProcessor {

    private static final BigDecimal MIN_LONGITUDE = BigDecimal.valueOf(-180);
    private static final BigDecimal MAX_LONGITUDE = BigDecimal.valueOf(180);
    private static final BigDecimal MIN_LATITUDE = BigDecimal.valueOf(-90);
    private static final BigDecimal MAX_LATITUDE = BigDecimal.valueOf(90);

    private final MerchantPlaceMatcher merchantPlaceMatcher;

    /**
     * 외부 장소 검색과 이름 신뢰도 계산이 끝난 최고 후보를 제공할 Matcher를 주입받는다.
     *
     * @param merchantPlaceMatcher 가맹점명으로 최고 신뢰도 장소 후보를 선정하는 컴포넌트
     */
    public MerchantClassificationProcessor(MerchantPlaceMatcher merchantPlaceMatcher) {
        this.merchantPlaceMatcher = merchantPlaceMatcher;
    }

    /**
     * 가맹점 사용 이력을 장소 검색 결과와 비교해 카카오 장소 정보가 포함된 최종 목록으로 만든다.
     *
     * <p>입력 목록이 비어 있으면 빈 불변 목록을 반환한다. 검색에 실패하거나 장소명을 사용할 수
     * 없는 가맹점은 미매칭 결과로 유지하고, 음식점·카페가 아닌 카테고리는 제외한다.</p>
     *
     * @param merchantUsages 선택 시간대에 맞춰 집계된 가맹점별 결제 사용 이력
     * @return 입력 순서를 유지한 음식점·카페·미분류 가맹점 사용 이력 불변 목록
     * @throws IllegalArgumentException 입력 목록이 null인 경우
     */
    public List<MerchantUsageData> classify(
            List<MerchantUsageData> merchantUsages
    ) {
        // 분류할 가맹점 사용 이력 목록이 존재하는지 공개 경계에서 검증한다.
        if (merchantUsages == null) {
            throw new IllegalArgumentException("merchantUsages는 null일 수 없습니다.");
        }

        Map<String, List<SearchCandidate>> searchCache = new HashMap<>();
        List<MerchantUsageData> classifiedUsages = new ArrayList<>(merchantUsages.size());

        // 입력 가맹점 순서대로 검색과 분류를 수행한다.
        for (MerchantUsageData merchantUsage : merchantUsages) {
            // 음식점·카페·미분류 결과만 최종 목록에 추가한다.
            classifyMerchant(merchantUsage, searchCache)
                    .ifPresent(classifiedUsages::add);
        }

        // 호출자가 가맹점 순서와 분류 결과를 변경하지 못하도록 불변 목록을 반환한다.
        return List.copyOf(classifiedUsages);
    }

    /**
     * 한 가맹점의 원본명과 비교용 이름을 순차 검색해 카카오 장소 결과를 결합한다.
     *
     * @param merchantUsage 분류할 한 가맹점의 결제 사용 이력
     * @param searchCache 현재 처리 호출 안에서 검색어별 결과를 재사용하는 캐시
     * @return 음식점·카페·미분류면 결과를 포함하고, 그 외 카테고리면 빈 Optional
     */
    private Optional<MerchantUsageData> classifyMerchant(
            MerchantUsageData merchantUsage,
            Map<String, List<SearchCandidate>> searchCache
    ) {
        // 원본명과 비교용 이름 검색을 Matcher에 위임해 최고 신뢰도 후보들을 받는다.
        MatchResult matchResult = merchantPlaceMatcher.match(
                merchantUsage.merchantName(),
                searchCache
        );

        // 검색할 이름이 없거나 이름 매칭 후보를 찾지 못하면 기존 미매칭 결과를 유지한다.
        if (!matchResult.matched()) {
            return Optional.of(merchantUsage);
        }

        // 최고 점수 후보들만 모아 카테고리 충돌과 좌표의 단일 후보 여부를 판단한다.
        List<SearchCandidate> bestCandidates = matchResult.bestCandidates();
        SearchCandidate selectedCandidate = bestCandidates.get(0);
        String categoryCode = determineCategoryCode(bestCandidates);

        // 음식점·카페·미분류가 아닌 명시적 카카오 카테고리는 최종 결과에서 제외한다.
        if (!isRetainedCategory(categoryCode)) {
            return Optional.empty();
        }

        // 최고 점수 후보가 하나일 때만 해당 장소의 좌표를 안전하게 변환한다.
        Coordinates coordinates = bestCandidates.size() == 1
                ? parseCoordinates(selectedCandidate)
                : Coordinates.empty();

        // 선택 장소의 추적 정보와 분류 결과를 하나의 카카오 장소 매칭 값으로 만든다.
        KakaoPlaceMatchData kakaoPlaceMatch = new KakaoPlaceMatchData(
                categoryCode,
                selectedCandidate.placeId(),
                selectedCandidate.placeName(),
                matchResult.matchConfidence(),
                coordinates.longitude(),
                coordinates.latitude()
        );

        // 원본 가맹점·결제 정보는 유지하고 카카오 장소 결과만 교체한 새 사용 이력을 반환한다.
        return Optional.of(merchantUsage.withKakaoPlaceMatch(kakaoPlaceMatch));
    }

    /**
     * 최고 점수 후보들의 카테고리가 모두 같은지 확인하고 최종 카테고리 코드를 결정한다.
     *
     * @param bestCandidates 같은 최고 매칭 점수를 받은 장소 후보 목록
     * @return 모든 코드가 같은 비공백 값이면 해당 코드, 공백이나 충돌이 있으면 UNKNOWN
     */
    private String determineCategoryCode(List<SearchCandidate> bestCandidates) {
        String resolvedCategoryCode = null;

        // 최고 점수 후보 전체를 확인해 하나의 카테고리로 합의되는지 검사한다.
        for (SearchCandidate candidate : bestCandidates) {
            String candidateCategoryCode = normalizeCategoryCode(
                    candidate.categoryCode()
            );

            // 카카오가 카테고리 그룹을 회신하지 않은 후보가 섞이면 미분류로 처리한다.
            if (candidateCategoryCode == null) {
                return KakaoPlaceMatchData.UNKNOWN_CATEGORY_CODE;
            }

            if (resolvedCategoryCode == null) {
                resolvedCategoryCode = candidateCategoryCode;
                continue;
            }

            // 같은 점수 후보들의 카테고리가 다르면 특정 카테고리로 단정하지 않는다.
            if (!resolvedCategoryCode.equals(candidateCategoryCode)) {
                return KakaoPlaceMatchData.UNKNOWN_CATEGORY_CODE;
            }
        }

        return resolvedCategoryCode == null
                ? KakaoPlaceMatchData.UNKNOWN_CATEGORY_CODE
                : resolvedCategoryCode;
    }

    /**
     * 카카오 카테고리 코드의 선택적 공백을 제거하고 미회신 값을 null로 통일한다.
     *
     * @param categoryCode 카카오가 회신한 카테고리 그룹 코드
     * @return 앞뒤 공백을 제거한 코드. null 또는 공백이면 null
     */
    private String normalizeCategoryCode(String categoryCode) {
        if (categoryCode == null || categoryCode.isBlank()) {
            return null;
        }
        return categoryCode.strip();
    }

    /**
     * 최종 AI 입력 후보에 유지할 음식점·카페·미분류 카테고리인지 확인한다.
     *
     * @param categoryCode 후보 합의로 결정된 카테고리 코드
     * @return FD6, CE7, UNKNOWN 중 하나면 true
     */
    private boolean isRetainedCategory(String categoryCode) {
        return KakaoPlaceMatchData.RESTAURANT_CATEGORY_CODE.equals(categoryCode)
                || KakaoPlaceMatchData.CAFE_CATEGORY_CODE.equals(categoryCode)
                || KakaoPlaceMatchData.UNKNOWN_CATEGORY_CODE.equals(categoryCode);
    }

    /**
     * 단일 최고 점수 후보의 경도·위도를 함께 파싱하고 유효 범위를 확인한다.
     *
     * @param candidate 좌표를 보강할 선택 장소 후보
     * @return 두 좌표가 모두 유효하면 좌표 값, 그렇지 않으면 빈 좌표
     */
    private Coordinates parseCoordinates(SearchCandidate candidate) {
        String rawLongitude = candidate.x();
        String rawLatitude = candidate.y();

        // 한 좌표라도 미회신이면 불완전한 위치를 저장하지 않는다.
        if (rawLongitude == null || rawLongitude.isBlank()
                || rawLatitude == null || rawLatitude.isBlank()) {
            return Coordinates.empty();
        }

        try {
            // 카카오가 문자열로 회신한 WGS84 경도와 위도를 정밀한 십진수로 변환한다.
            BigDecimal longitude = new BigDecimal(rawLongitude);
            BigDecimal latitude = new BigDecimal(rawLatitude);

            // 범위를 벗어난 외부 좌표는 장소 분류를 실패시키지 않고 미보강으로 처리한다.
            if (!isWithinRange(longitude, MIN_LONGITUDE, MAX_LONGITUDE)
                    || !isWithinRange(latitude, MIN_LATITUDE, MAX_LATITUDE)) {
                return Coordinates.empty();
            }

            return new Coordinates(longitude, latitude);
        } catch (NumberFormatException exception) {
            // 좌표는 선택값이므로 잘못된 외부 문자열은 분류 실패 대신 좌표 미보강으로 처리한다.
            return Coordinates.empty();
        }
    }

    /**
     * 외부 좌표가 지정한 경도 또는 위도 범위 안에 있는지 확인한다.
     *
     * @param coordinate 검사할 좌표
     * @param minimum 허용 최솟값
     * @param maximum 허용 최댓값
     * @return 최솟값 이상 최댓값 이하이면 true
     */
    private boolean isWithinRange(
            BigDecimal coordinate,
            BigDecimal minimum,
            BigDecimal maximum
    ) {
        return coordinate.compareTo(minimum) >= 0
                && coordinate.compareTo(maximum) <= 0;
    }

    /**
     * 검증된 경도·위도 쌍 또는 두 값이 모두 없는 상태를 표현하는 내부 값이다.
     *
     * @param longitude 경도. 좌표 미보강 상태면 null
     * @param latitude 위도. 좌표 미보강 상태면 null
     */
    private record Coordinates(
            BigDecimal longitude,
            BigDecimal latitude
    ) {

        /**
         * 외부 좌표를 사용할 수 없거나 장소를 하나로 확정하지 못한 상태를 반환한다.
         *
         * @return 경도와 위도가 모두 null인 빈 좌표
         */
        private static Coordinates empty() {
            return new Coordinates(null, null);
        }
    }
}
