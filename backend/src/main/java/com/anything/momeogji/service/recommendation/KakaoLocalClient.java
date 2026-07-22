package com.anything.momeogji.service.recommendation;

import com.anything.momeogji.dto.recommendation.RestaurantCandidate;

import java.util.List;

// 카카오 로컬 API(키워드 검색)로 좌표 주변의 실제 음식점 후보를 찾기.
public interface KakaoLocalClient {

    // 검색 결과. 검색이 실패하거나(네트워크 오류 등) 결과가 없으면 빈 리스트를 반환한다(예외를 던지지 않음).
    List<RestaurantCandidate> searchNearby(String keyword, double longitude, double latitude, int radiusMeters, int size);
}
