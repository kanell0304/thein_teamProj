package com.anything.momeogji.mydata.transform;

import com.anything.momeogji.mydata.model.UserMyData;
import com.anything.momeogji.mydata.transform.model.CleanApprovalData;
import com.anything.momeogji.mydata.transform.model.MerchantUsageData;
import com.anything.momeogji.mydata.transform.model.TransformedUserMyData;
import org.springframework.stereotype.Component;

import java.time.LocalTime;
import java.util.List;

/**
 * 참가자 한 명의 수집된 마이데이터를 카카오 장소 정보가 포함된 가맹점 사용 이력까지 순서대로 가공한다.
 *
 * <p>이 컴포넌트는 {@link UserMyDataCleaner}, {@link MerchantUsageProcessor},
 * {@link MerchantClassificationProcessor}를 정해진 순서로 호출하고 결과를 조립하는 책임만 가진다.
 * 각 단계의 상태 필터, 중복 제거, 시간대 집계와 장소 매칭 규칙은 기존 컴포넌트에 위임한다.</p>
 *
 * <p>중간 단계에서 데이터 계약 오류가 발생하면 예외를 감추지 않는다. 외부 장소 검색 실패는
 * 검색 Client의 기존 계약에 따라 빈 후보로 바뀌고 최종 목록에서는 제외된다.</p>
 */
@Component
public class MyDataTransformer {

    private final UserMyDataCleaner userMyDataCleaner;
    private final MerchantUsageProcessor merchantUsageProcessor;
    private final MerchantClassificationProcessor merchantClassificationProcessor;

    /**
     * 전체 가공 순서를 구성할 정제·집계·분류 컴포넌트를 주입받는다.
     *
     * @param userMyDataCleaner 사용할 수 없는 승인내역과 정확한 중복을 제거하는 컴포넌트
     * @param merchantUsageProcessor 선택 시간대의 동일 가맹점 결제를 묶는 컴포넌트
     * @param merchantClassificationProcessor 가맹점 사용 이력에 카테고리와 장소 정보를 보강하는 컴포넌트
     */
    public MyDataTransformer(
            UserMyDataCleaner userMyDataCleaner,
            MerchantUsageProcessor merchantUsageProcessor,
            MerchantClassificationProcessor merchantClassificationProcessor
    ) {
        this.userMyDataCleaner = userMyDataCleaner;
        this.merchantUsageProcessor = merchantUsageProcessor;
        this.merchantClassificationProcessor = merchantClassificationProcessor;
    }

    /**
     * 수집된 참가자 마이데이터를 1차 정제, 시간대별 가맹점 집계, 카테고리 분류 순서로 가공한다.
     *
     * <p>승인내역이 없거나 선택 시각이 속한 시간대에 해당하는 결제가 없으면 사용자 ID와
     * 빈 분류 목록을 반환한다. 각 단계의 입력 데이터가 잘못된 경우 해당 단계의 예외를 전달한다.</p>
     *
     * @param userMyData 수집·검증·파싱이 끝난 참가자 마이데이터
     * @param meetingTime 옵션 계층에서 검증한 마이데이터 필터 기준 시각
     * @param categoryGroupCode 모임 목적에 맞춰 선택한 음식점 {@code FD6} 또는 카페 {@code CE7} 코드
     * @return 사용자 ID와 최종 가맹점 분류 목록을 포함한 불변 결과
     * @throws IllegalArgumentException 입력 마이데이터 또는 선택 시각이 없는 경우
     */
    public TransformedUserMyData transform(
            UserMyData userMyData,
            LocalTime meetingTime,
            String categoryGroupCode
    ) {
        // 공개 조립 경계에서 참가자 마이데이터가 존재하는지 먼저 검증한다.
        if (userMyData == null) {
            throw new IllegalArgumentException("userMyData는 null일 수 없습니다.");
        }

        // 잘못된 시각으로 앞 단계 정제를 수행하지 않도록 선택 시각을 먼저 검증한다.
        if (meetingTime == null) {
            throw new IllegalArgumentException("meetingTime은 필수입니다.");
        }

        // 승인 상태와 가맹점 정보 누락을 확인하고 정확히 중복된 결제를 제거한다.
        List<CleanApprovalData> cleanedApprovals = userMyDataCleaner.clean(userMyData);

        // 선택 시각이 속한 시간대의 결제만 남긴 뒤 동일 가맹점별 사용 이력으로 집계한다.
        List<MerchantUsageData> merchantUsages = merchantUsageProcessor.process(
                cleanedApprovals,
                meetingTime
        );

        // 가맹점명을 외부 장소 후보와 비교해 기존 사용 이력에 카카오 장소 결과를 보강한다.
        List<MerchantUsageData> classifiedMerchantUsages =
                merchantClassificationProcessor.classify(
                        merchantUsages,
                        categoryGroupCode
                );

        // 내부 시간대 정보는 폐기하고 사용자 식별자와 최종 가맹점 사용 이력만 반환한다.
        return new TransformedUserMyData(
                userMyData.userId(),
                classifiedMerchantUsages
        );
    }
}
