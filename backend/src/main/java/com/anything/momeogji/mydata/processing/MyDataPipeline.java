package com.anything.momeogji.mydata.processing;

import com.anything.momeogji.mydata.collection.model.CollectedUserMyData;
import com.anything.momeogji.mydata.processing.place.MerchantPlaceClassifier;
import com.anything.momeogji.mydata.processing.model.CleanedApprovalData;
import com.anything.momeogji.mydata.processing.model.MerchantUsageData;
import com.anything.momeogji.mydata.processing.model.MyDataRestaurantData;
import org.springframework.stereotype.Component;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

/**
 * 참가자 한 명의 수집된 마이데이터를 카카오 장소 정보가 포함된 가맹점 사용 이력까지 순서대로 가공한다.
 *
 * <p>이 컴포넌트는 {@link UserMyDataCleaner}, {@link MerchantUsageProcessor},
 * {@link MerchantPlaceClassifier}를 정해진 순서로 호출하고 결과를 조립하는 책임만 가진다.
 * 각 단계의 상태 필터, 중복 제거, 시간대 집계와 장소 매칭 규칙은 기존 컴포넌트에 위임한다.</p>
 *
 * <p>중간 단계에서 데이터 계약 오류가 발생하면 예외를 감추지 않는다. 외부 장소 검색 실패는
 * 검색 Client의 기존 계약에 따라 빈 후보로 바뀌고 최종 목록에서는 제외된다.</p>
 */
@Component
public class MyDataPipeline {

    private static final Set<String> CATEGORY_GROUP_NAMES =
            Set.of("음식점", "카페");

    private final UserMyDataCleaner userMyDataCleaner;
    private final MerchantUsageProcessor merchantUsageProcessor;
    private final MerchantPlaceClassifier merchantPlaceClassifier;

    /**
     * 전체 가공 순서를 구성할 정제·집계·분류 컴포넌트를 주입받는다.
     *
     * @param userMyDataCleaner 승인 상태와 가맹점 정보를 기준으로 결제를 정제하는 컴포넌트
     * @param merchantUsageProcessor 선택 시간대의 동일 가맹점 결제를 묶는 컴포넌트
     * @param merchantPlaceClassifier 가맹점 사용 이력에 카카오 장소 정보를 보강하는 컴포넌트
     */
    public MyDataPipeline(
            UserMyDataCleaner userMyDataCleaner,
            MerchantUsageProcessor merchantUsageProcessor,
            MerchantPlaceClassifier merchantPlaceClassifier
    ) {
        this.userMyDataCleaner = userMyDataCleaner;
        this.merchantUsageProcessor = merchantUsageProcessor;
        this.merchantPlaceClassifier = merchantPlaceClassifier;
    }

    /**
     * 수집된 사용자 마이데이터를 1차 정제, 시간대별 가맹점 집계, 카카오 장소 분류 순서로 가공한다.
     *
     * <p>승인내역이 없거나 선택 시각이 속한 시간대에 해당하는 결제가 없으면 빈 음식점 목록을 반환한다.
     * 각 단계의 입력 데이터가 잘못된 경우 해당 단계의 예외를 전달한다.</p>
     *
     * @param collectedMyData 수집·검증·파싱이 끝난 사용자 마이데이터
     * @param meetingTime 옵션 계층에서 검증한 마이데이터 필터 기준 시각
     * @param categoryGroupCode 모임 목적에 맞춰 선택한 음식점 {@code FD6} 또는 카페 {@code CE7} 코드
     * @return 카카오 장소명과 음식 카테고리만 포함한 불변 음식점 목록
     * @throws IllegalArgumentException 수집된 마이데이터 또는 선택 시각이 없는 경우
     */
    public List<MyDataRestaurantData> execute(
            CollectedUserMyData collectedMyData,
            LocalTime meetingTime,
            String categoryGroupCode
    ) {
        // 공개 조립 경계에서 수집된 사용자 마이데이터가 존재하는지 먼저 검증한다.
        if (collectedMyData == null) {
            throw new IllegalArgumentException("collectedMyData는 null일 수 없습니다.");
        }

        // 잘못된 시각으로 앞 단계 정제를 수행하지 않도록 선택 시각을 먼저 검증한다.
        if (meetingTime == null) {
            throw new IllegalArgumentException("meetingTime은 필수입니다.");
        }

        // 승인 상태와 가맹점 정보 누락을 확인하고 정확히 중복된 결제를 제거한다.
        List<CleanedApprovalData> cleanedApprovals = userMyDataCleaner.clean(collectedMyData);

        // 선택 시각이 속한 시간대의 결제만 남긴 뒤 동일 가맹점별 사용 이력으로 집계한다.
        List<MerchantUsageData> merchantUsages = merchantUsageProcessor.process(
                cleanedApprovals,
                meetingTime
        );

        // 가맹점명을 외부 장소 후보와 비교해 기존 사용 이력에 카카오 장소 결과를 보강한다.
        List<MerchantUsageData> classifiedMerchantUsages =
                merchantPlaceClassifier.classify(
                        merchantUsages,
                        categoryGroupCode
                );

        // 내부 결제·가맹점 정보는 폐기하고 AI에 전달할 장소명과 음식 카테고리만 추출한다.
        return toRestaurantData(classifiedMerchantUsages);
    }

    /**
     * 카카오 매칭이 끝난 가맹점 목록을 AI 전달용 음식점 목록으로 변환한다.
     *
     * @param merchantUsages 카카오 장소명과 전체 카테고리 경로가 채워진 가맹점 사용 이력
     * @return 입력 순서와 중복을 유지한 불변 음식점 목록
     */
    private List<MyDataRestaurantData> toRestaurantData(
            List<MerchantUsageData> merchantUsages
    ) {
        List<MyDataRestaurantData> restaurants = new ArrayList<>();

        for (MerchantUsageData merchantUsage : merchantUsages) {
            // 카카오 전체 경로에서 음식점·카페 대분류만 제거한다.
            String foodCategory = extractFoodCategory(
                    merchantUsage.kakaoPlaceMatch().categoryName()
            );

            // 대분류 제거 후 카테고리가 남지 않은 장소는 AI 전달 대상에서 제외한다.
            if (foodCategory == null) {
                continue;
            }

            // 사용자 식별자와 결제 상세를 제외한 최소 음식점 항목을 생성한다.
            restaurants.add(new MyDataRestaurantData(
                    merchantUsage.kakaoPlaceMatch().placeName(),
                    foodCategory
            ));
        }

        // 외부에서 결과 순서나 중복 구성을 변경하지 못하도록 불변 목록으로 반환한다.
        return List.copyOf(restaurants);
    }

    /**
     * 카카오 전체 카테고리 경로를 AI가 사용할 음식 카테고리 경로로 정리한다.
     *
     * <p>첫 구간이 음식점 또는 카페이면 해당 검색 대분류만 제거한다.
     * 인식할 수 없는 첫 구간은 실제 음식 카테고리일 수 있으므로 전체 경로를 유지한다.</p>
     *
     * @param categoryName 카카오가 반환한 {@code >} 구분 전체 카테고리 경로
     * @return 공백을 정리한 음식 카테고리 경로, 남는 구간이 없으면 {@code null}
     */
    private String extractFoodCategory(String categoryName) {
        // 각 경로 구간의 앞뒤 공백을 제거하고 빈 구간은 사용하지 않는다.
        List<String> segments = Arrays.stream(categoryName.split(">"))
                .map(String::strip)
                .filter(segment -> !segment.isEmpty())
                .toList();

        if (segments.isEmpty()) {
            return null;
        }

        // 음식점·카페는 검색 범위를 나타내는 대분류이므로 최종 음식 카테고리에서 제외한다.
        int firstFoodCategoryIndex =
                CATEGORY_GROUP_NAMES.contains(segments.getFirst()) ? 1 : 0;
        if (firstFoodCategoryIndex >= segments.size()) {
            return null;
        }

        // 세부 단계는 버리지 않고 표준 구분자로 다시 연결한다.
        return String.join(
                " > ",
                segments.subList(firstFoodCategoryIndex, segments.size())
        );
    }
}
