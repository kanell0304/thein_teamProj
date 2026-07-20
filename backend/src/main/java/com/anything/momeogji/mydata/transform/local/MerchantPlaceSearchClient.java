package com.anything.momeogji.mydata.transform.local;

import java.util.List;

/**
 * 이 파일은 마이데이터 가맹점명으로 외부 장소를 검색하는 인터페이스
 *
 * 구현체는 검색 결과가 없거나 외부 API 호출에 실패하면 빈 목록을 반환
 * 카테고리와 좌표는 외부 제공자가 회신하지 않을 수 있으므로 원본 문자열 상태로 전달
 */
public interface MerchantPlaceSearchClient {

    /**
     * 가맹점명 검색어와 일치할 가능성이 있는 장소 후보를 제공자 응답 순서대로 조회
     *
     * @param query 외부 장소 검색에 전달할 원본 또는 비교용 가맹점명
     * @return 제공자 응답 순서를 유지한 불변 장소 후보 목록. 결과가 없거나 호출에 실패하면 빈 목록
     * @throws IllegalArgumentException 검색어가 null 또는 공백인 경우
     */
    List<SearchCandidate> search(String query);

    /**
     * 외부 장소 검색 응답 중 카테고리 분류와 매칭 근거 보존에 필요한 최소 필드다.
     *
     * @param placeId 외부 제공자가 발급한 장소 식별자
     * @param placeName 외부 제공자에 등록된 장소명
     * @param categoryCode 외부 제공자의 카테고리 그룹 코드. 미회신이면 null 또는 공백
     * @param x 경도 원본 문자열. 미회신이면 null 또는 공백
     * @param y 위도 원본 문자열. 미회신이면 null 또는 공백
     */
    record SearchCandidate(
            String placeId,
            String placeName,
            String categoryCode,
            String x,
            String y
    ) {
    }
}
