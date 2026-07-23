package com.anything.momeogji.mydata.processing.place;

import java.util.List;

/**
 * 이 파일은 마이데이터 가맹점명으로 외부 장소를 검색하는 인터페이스
 *
 * 구현체는 검색 결과가 없거나 외부 API 호출에 실패하면 빈 목록을 반환한다.
 * 요청한 음식점·카페 그룹과 일치하고 세부 카테고리를 확인할 수 있는 장소만 전달한다.
 */
public interface MerchantPlaceSearchClient {

    /**
     * 가맹점명 검색어와 일치할 가능성이 있는 장소 후보를 제공자 응답 순서대로 조회
     *
     * @param query 외부 장소 검색에 전달할 원본 또는 비교용 가맹점명
     * @param categoryGroupCode 음식점 {@code FD6} 또는 카페 {@code CE7} 그룹 코드
     * @return 제공자 응답 순서를 유지한 불변 장소 후보 목록. 결과가 없거나 호출에 실패하면 빈 목록
     * @throws IllegalArgumentException 검색어가 null 또는 공백이거나 그룹 코드가 허용값이 아닌 경우
     */
    List<SearchCandidate> search(String query, String categoryGroupCode);

    /**
     * 외부 장소 검색 응답 중 카테고리 분류와 매칭 근거 보존에 필요한 최소 필드다.
     *
     * @param placeName 외부 제공자에 등록된 장소명
     * @param categoryGroupCode 외부 제공자의 중요 카테고리 그룹 코드
     * @param categoryName 외부 제공자의 전체 세부 카테고리 경로
     */
    record SearchCandidate(
            String placeName,
            String categoryGroupCode,
            String categoryName
    ) {
    }
}
