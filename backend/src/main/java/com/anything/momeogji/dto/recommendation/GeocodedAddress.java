package com.anything.momeogji.dto.recommendation;

/** 카카오 로컬 API로 검증한 주소/좌표. 값을 못 찾은 필드는 null로 두고 호출 측에서 AI 원본값으로 대체한다. */
public record GeocodedAddress(String roadAddress, String address, Double latitude, Double longitude) {
}
