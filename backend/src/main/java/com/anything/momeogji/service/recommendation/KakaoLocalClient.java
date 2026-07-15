package com.anything.momeogji.service.recommendation;

import com.anything.momeogji.dto.recommendation.GeocodedAddress;

import java.util.Optional;

/** 카카오 로컬 API(주소 검색)로 텍스트 주소를 검증된 좌표로 변환한다. */
public interface KakaoLocalClient {

    /**
     * @return 주소를 찾았으면 검증된 좌표/주소, 못 찾았거나 API 호출이 실패하면 {@link Optional#empty()}
     */
    Optional<GeocodedAddress> searchAddress(String query);
}
