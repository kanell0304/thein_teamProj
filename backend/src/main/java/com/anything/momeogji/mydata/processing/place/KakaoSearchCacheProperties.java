package com.anything.momeogji.mydata.processing.place;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * MyData 가맹점명으로 조회한 카카오 장소 후보 캐시의 만료 시간과 최대 항목 수를 제공한다.
 *
 * @param ttl 캐시 항목을 저장한 시점부터 유지할 시간
 * @param maximumSize 캐시에 유지할 수 있는 최대 검색 키 개수
 */
@ConfigurationProperties(prefix = "mydata.kakao-search-cache")
public record KakaoSearchCacheProperties(
        Duration ttl,
        long maximumSize
) {

    /**
     * 애플리케이션 시작 시 누락되거나 사용할 수 없는 캐시 설정을 거부한다.
     */
    public KakaoSearchCacheProperties {
        // 만료 시간을 적용할 수 있도록 null과 0 이하 값을 거부한다.
        if (ttl == null || ttl.isZero() || ttl.isNegative()) {
            throw new IllegalArgumentException(
                    "mydata.kakao-search-cache.ttl은 0보다 커야 합니다."
            );
        }

        // 무제한 캐시가 되지 않도록 최대 항목 수를 양수로 제한한다.
        if (maximumSize < 1) {
            throw new IllegalArgumentException(
                    "mydata.kakao-search-cache.maximum-size는 1 이상이어야 합니다."
            );
        }
    }
}
