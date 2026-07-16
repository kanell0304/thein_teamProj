package com.anything.momeogji.service.recommendation;

import java.util.Optional;

/**
 * 구글 Places API(New)로 음식점의 대표 이미지를 찾는다.
 * 카카오 이미지 검색과 달리 특정 Place(업체)에 실제로 등록된 사진이라 정확도가 더 높다.
 * 좌표를 locationBias로 함께 넘겨 동명의 다른 지점과 헷갈리지 않도록 한다.
 */
public interface GooglePlacesImageClient {

    /**
     * @return 검색된 첫 번째 사진 URL. API 키가 없거나, 결과가 없거나, 호출이 실패하면 빈 Optional(예외를 던지지 않음).
     */
    Optional<String> searchFirstImageUrl(String name, double latitude, double longitude);
}
