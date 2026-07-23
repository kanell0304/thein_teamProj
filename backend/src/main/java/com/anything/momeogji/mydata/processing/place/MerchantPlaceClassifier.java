package com.anything.momeogji.mydata.processing.place;

import com.anything.momeogji.mydata.processing.place.MerchantPlaceMatcher.MatchResult;
import com.anything.momeogji.mydata.processing.place.MerchantPlaceSearchClient.SearchCandidate;
import com.anything.momeogji.mydata.processing.model.KakaoPlaceMatchData;
import com.anything.momeogji.mydata.processing.model.MerchantUsageData;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * 가맹점별 결제 사용 이력 중 모임 목적에 맞는 카카오 장소와 세부 카테고리를 확인한 결과만 남긴다.
 *
 * <p>장소 검색과 이름 일치 우선순위 판정은 {@link MerchantPlaceMatcher}에 위임한다.
 * 검색 실패, 이름 미매칭, 요청 그룹 불일치, 세부 카테고리 누락·충돌은 오류가 아니라
 * AI에 전달할 수 없는 결과로 처리해 최종 목록에서 제외한다.</p>
 *
 * <p>매칭에 성공하면 기존 결제 이력은 유지하고 카카오 장소명과 전체 카테고리 경로만 결합한다.
 * 입력 가맹점의 원래 순서를 보존한 불변 목록을 반환한다.</p>
 */
@Component
public class MerchantPlaceClassifier {

    private static final Set<String> ALLOWED_CATEGORY_GROUP_CODES = Set.of("FD6", "CE7");

    private final MerchantPlaceMatcher merchantPlaceMatcher;

    /**
     * 외부 장소 검색과 이름 비교가 끝난 최고 후보를 제공할 Matcher를 주입받는다.
     *
     * @param merchantPlaceMatcher 목적별 장소 후보 중 최고 이름 매칭 후보를 선정하는 컴포넌트
     */
    public MerchantPlaceClassifier(MerchantPlaceMatcher merchantPlaceMatcher) {
        this.merchantPlaceMatcher = merchantPlaceMatcher;
    }

    /**
     * 가맹점 사용 이력을 요청한 음식점·카페 그룹으로 검색하고 최종 매칭 결과만 반환한다.
     *
     * @param merchantUsages 선택 시간대에 맞춰 집계된 가맹점별 결제 사용 이력
     * @param categoryGroupCode 음식점 {@code FD6} 또는 카페 {@code CE7} 그룹 코드
     * @return 입력 순서를 유지한 장소명·세부 카테고리 확인 완료 목록
     * @throws IllegalArgumentException 입력 목록이 null이거나 그룹 코드가 허용값이 아닌 경우
     */
    public List<MerchantUsageData> classify(
            List<MerchantUsageData> merchantUsages,
            String categoryGroupCode
    ) {
        // 분류할 가맹점 사용 이력 목록이 존재하는지 공개 경계에서 검증한다.
        if (merchantUsages == null) {
            throw new IllegalArgumentException("merchantUsages는 null일 수 없습니다.");
        }

        // 카카오 키워드 검색에서 지원할 음식점·카페 그룹만 허용한다.
        if (!ALLOWED_CATEGORY_GROUP_CODES.contains(categoryGroupCode)) {
            throw new IllegalArgumentException("categoryGroupCode는 FD6 또는 CE7이어야 합니다.");
        }

        Map<String, List<SearchCandidate>> searchCache = new HashMap<>();
        List<MerchantUsageData> classifiedUsages = new ArrayList<>(merchantUsages.size());

        // 입력 가맹점 순서대로 목적 그룹 검색과 장소명 매칭을 수행한다.
        for (MerchantUsageData merchantUsage : merchantUsages) {
            // 장소명과 세부 카테고리를 모두 확정한 결과만 최종 목록에 추가한다.
            classifyMerchant(merchantUsage, categoryGroupCode, searchCache)
                    .ifPresent(classifiedUsages::add);
        }

        // 호출자가 가맹점 순서와 분류 결과를 변경하지 못하도록 불변 목록을 반환한다.
        return List.copyOf(classifiedUsages);
    }

    /**
     * 한 가맹점의 이름을 목적 그룹 안에서 검색하고 합의된 세부 카테고리를 결합한다.
     *
     * @param merchantUsage 분류할 한 가맹점의 결제 사용 이력
     * @param categoryGroupCode 현재 모임 목적에 대응하는 카카오 그룹 코드
     * @param searchCache 현재 처리 호출 안에서 검색어별 결과를 재사용하는 캐시
     * @return 장소명과 세부 카테고리를 모두 확정하면 결과를 포함하고 그렇지 않으면 빈 Optional
     */
    private Optional<MerchantUsageData> classifyMerchant(
            MerchantUsageData merchantUsage,
            String categoryGroupCode,
            Map<String, List<SearchCandidate>> searchCache
    ) {
        // 원본명과 비교용 이름 검색을 Matcher에 위임해 동일 우선순위 후보들을 받는다.
        MatchResult matchResult = merchantPlaceMatcher.match(
                merchantUsage.merchantName(),
                categoryGroupCode,
                searchCache
        );

        // 검색할 이름이 없거나 허용 가능한 이름 매칭 후보가 없으면 최종 목록에서 제외한다.
        if (!matchResult.hasMatches()) {
            return Optional.empty();
        }

        List<SearchCandidate> priorityCandidates = matchResult.priorityCandidates();

        // 후보의 카테고리가 같으면 첫 후보를, 충돌하면 유일하게 가장 유사한 후보를 선택한다.
        Optional<SearchCandidate> resolvedCandidate = resolveCandidate(
                merchantUsage.merchantName(),
                priorityCandidates,
                categoryGroupCode
        );
        if (resolvedCandidate.isEmpty()) {
            return Optional.empty();
        }

        SearchCandidate selectedCandidate = resolvedCandidate.get();
        String placeName = stripToNull(selectedCandidate.placeName());
        String categoryName = stripToNull(selectedCandidate.categoryName());

        // 최종 결과에는 AI가 사용할 장소명과 전체 카테고리 경로만 보존한다.
        KakaoPlaceMatchData kakaoPlaceMatch = new KakaoPlaceMatchData(
                placeName,
                categoryName
        );

        // 원본 가맹점·결제 정보는 유지하고 확인된 카카오 장소 결과만 교체한다.
        return Optional.of(merchantUsage.withKakaoPlaceMatch(kakaoPlaceMatch));
    }

    /**
     * 동일 이름 일치 우선순위 후보를 검증하고 카테고리 충돌 정책에 따라 최종 후보를 선택한다.
     *
     * @param merchantName 마이데이터의 원본 가맹점명
     * @param priorityCandidates 동일한 이름 일치 우선순위를 가진 장소 후보 목록
     * @param requestedCategoryGroupCode 카카오 요청에 사용한 음식점 또는 카페 그룹 코드
     * @return 카테고리가 같으면 첫 후보, 충돌하면 유일한 최고 유사도 후보, 결정할 수 없으면 빈 Optional
     */
    private Optional<SearchCandidate> resolveCandidate(
            String merchantName,
            List<SearchCandidate> priorityCandidates,
            String requestedCategoryGroupCode
    ) {
        String resolvedCategoryName = null;
        boolean categoryConflict = false;

        // 동일 우선순위 후보 전체의 그룹 코드, 장소명, 세부 카테고리를 먼저 검증한다.
        for (SearchCandidate candidate : priorityCandidates) {
            if (candidate == null
                    || !requestedCategoryGroupCode.equals(candidate.categoryGroupCode())
                    || stripToNull(candidate.placeName()) == null) {
                return Optional.empty();
            }

            String candidateCategoryName = stripToNull(candidate.categoryName());
            if (candidateCategoryName == null) {
                return Optional.empty();
            }

            if (resolvedCategoryName == null) {
                resolvedCategoryName = candidateCategoryName;
                continue;
            }

            // 하나라도 다른 세부 카테고리가 있으면 내부 이름 유사도로 충돌을 해소한다.
            if (!resolvedCategoryName.equals(candidateCategoryName)) {
                categoryConflict = true;
            }
        }

        // 모든 세부 카테고리가 같으면 외부 응답 순서의 첫 후보를 대표값으로 사용한다.
        if (!categoryConflict) {
            return Optional.of(priorityCandidates.get(0));
        }

        // 카테고리 충돌 시에만 내부 길이 비율이 유일하게 가장 높은 후보를 선택한다.
        return merchantPlaceMatcher.selectUniqueBestLengthMatch(
                merchantName,
                priorityCandidates
        );
    }

    /**
     * 카카오 문자열의 앞뒤 공백을 제거하고 미회신·공백 값을 null로 통일한다.
     *
     * @param value 카카오가 회신한 선택 문자열
     * @return 앞뒤 공백을 제거한 값. 미회신 또는 공백이면 null
     */
    private String stripToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.strip();
    }
}
