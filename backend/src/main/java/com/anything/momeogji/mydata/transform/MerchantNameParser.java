package com.anything.momeogji.mydata.transform;

import org.springframework.stereotype.Component;

import java.text.Normalizer;
import java.util.Locale;

/**
 * 원본 가맹점명을 변경하지 않고 집계와 검색 결과 비교에 사용할 문자열 키를 생성
 *
 * 비교할 때만 Unicode NFKC 정규화, 영문 소문자화와 모든 공백 제거를 적용한 별도 키 생성
 * 지점명이나 법인명처럼 실제로 다른 가맹점을 구분할 수 있는 문구는 임의로 제거하지 않는다.
 */
@Component
public class MerchantNameParser {

    /**
     * 선택적으로 제공되는 원본 가맹점명에서 비교 전용 문자열 키를 생성
     *
     * @param merchantName 원본 가맹점명. 미회신이면 null
     * @return 가맹점명이 없으면 null, 있으면 NFKC·소문자·공백 제거를 적용한 비교 키
     * @throws IllegalArgumentException 가맹점명이 공백이거나 변환 후 비교할 문자가 남지 않는 경우
     */
    public String createComparisonKey(String merchantName) {
        // 가맹점명이 미회신된 경우 번호만 사용하는 집계 키를 만들 수 있도록 null을 유지한다.
        if (merchantName == null) {
            return null;
        }

        // 값이 회신됐다면 공백 문자열을 정상 가맹점명으로 취급하지 않는다.
        if (merchantName.isBlank()) {
            throw new IllegalArgumentException("merchantName은 공백일 수 없습니다.");
        }

        // 호환 문자를 표준 형태로 맞춘 뒤 영문 대소문자 차이를 제거한다.
        String normalized = Normalizer.normalize(merchantName, Normalizer.Form.NFKC)
                .toLowerCase(Locale.ROOT);
        StringBuilder comparisonKey = new StringBuilder(normalized.length());

        // 일반 공백과 Unicode 공간 문자를 제거하되 나머지 상호명 문자는 그대로 보존한다.
        normalized.codePoints()
                .filter(codePoint -> !Character.isWhitespace(codePoint)
                        && !Character.isSpaceChar(codePoint))
                .forEach(comparisonKey::appendCodePoint);

        // 공백 제거 후 비교할 문자가 남아 있는지 검증한다.
        if (comparisonKey.isEmpty()) {
            throw new IllegalArgumentException("merchantName에서 비교할 문자를 찾을 수 없습니다.");
        }

        return comparisonKey.toString();
    }
}
