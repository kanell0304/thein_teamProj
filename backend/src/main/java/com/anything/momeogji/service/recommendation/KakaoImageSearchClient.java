package com.anything.momeogji.service.recommendation;

import java.util.Optional;


 // 카카오 이미지 검색 API로 음식점 이름 기준 대표 이미지를 찾는다.
 // 일반 웹 이미지 검색이라 그 가게의 공식 사진이라는 보장은 없다(참고용 대표 이미지).

public interface KakaoImageSearchClient {


    //검색된 첫 번째 이미지 URL. 결과가 없거나 검색이 실패하면 빈 Optional(예외를 던지지 않음).
    Optional<String> searchFirstImageUrl(String keyword);
}
