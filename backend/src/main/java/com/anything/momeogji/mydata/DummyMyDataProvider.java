package com.anything.momeogji.mydata;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.regex.Pattern;

/**
 * 실제 금융 마이데이터 API 대신 classpath의 참가자별 Dummy JSON을 반환한다.
 *
 * <p>공개 메서드의 상세 호출 조건과 반환 계약은 {@link MyDataProvider}를 참조한다.
 * 이 클래스에는 classpath 파일 조회와 Dummy 요청값 처리 같은 구현 세부사항만 둔다.</p>
 */
@Component
public class DummyMyDataProvider implements MyDataProvider {

    private static final String DUMMY_BASE_PATH = "mydata/dummy";
    private static final Pattern SAFE_CARD_ID = Pattern.compile("[A-Za-z0-9_-]{1,64}");
    private static final Pattern SAFE_PAGE_TOKEN = Pattern.compile("page-\\d{3}");
    private static final String FIRST_PAGE_TOKEN = "page-001";

    /**
     * 상세 호출 계약은 {@link MyDataProvider#fetchCardList(Long, String, String, int)}를 참조한다.
     */
    @Override
    public String fetchCardList(Long userId, String searchTimestamp, String nextPage, int limit) {
        validUserId(userId);

        String resourcePath = getDirectory(userId) + "/card-list.json";
        return getDummyFile(resourcePath);
    }

    /**
     * 상세 호출 계약은
     * {@link MyDataProvider#fetchDomesticApprovals(Long, String, LocalDate, LocalDate, String, int)}를
     * 참조한다.
     */
    @Override
    public String fetchDomesticApprovals(Long userId, String cardId, LocalDate fromDate,
                                         LocalDate toDate, String nextPage, int limit) {
        validUserId(userId);
        validCardId(cardId);
        String pageToken = nextPage == null ? FIRST_PAGE_TOKEN : validNextPage(nextPage);

        String resourcePath = getDirectory(userId)
                + "/approval-domestic-" + cardId + "-" + pageToken + ".json";
        return getDummyFile(resourcePath);
    }

    /**
     * 사용자 ID를 classpath의 {@code user-숫자} 형식 Dummy 디렉터리 경로로 변환한다.
     *
     * @param userId 검증이 끝난 사용자 내부 ID
     * @return {@code mydata/dummy/user-01} 형식의 classpath 상대 경로
     */
    private String getDirectory(Long userId) {
        return DUMMY_BASE_PATH + "/user-%02d".formatted(userId);
    }

    /**
     * 필수 Dummy 리소스를 UTF-8 문자열로 읽고 파일의 존재 여부와 빈 내용을 확인한다.
     *
     * @param resourcePath classpath 기준 JSON 파일 경로
     * @return 파일의 가공되지 않은 JSON 문자열
     * @throws IllegalStateException 파일이 없거나 비어 있거나 입출력 오류가 발생한 경우
     */
    private String getDummyFile(String resourcePath) {
        ClassPathResource resource = new ClassPathResource(resourcePath);
        if (!resource.exists() || !resource.isReadable()) {
            throw new IllegalStateException("Dummy 마이데이터 파일을 찾을 수 없습니다: " + resourcePath);
        }

        try {
            String rawJson = resource.getContentAsString(StandardCharsets.UTF_8);
            if (rawJson.isBlank()) {
                throw new IllegalStateException("Dummy 마이데이터 파일이 비어 있습니다: " + resourcePath);
            }
            return rawJson;
        } catch (IOException exception) {
            throw new IllegalStateException(
                    "Dummy 마이데이터 파일을 읽을 수 없습니다: " + resourcePath,
                    exception
            );
        }
    }

    /**
     * 사용자 ID가 Dummy 디렉터리 이름으로 사용할 수 있는 양수인지 확인한다.
     *
     * @param userId 검사할 사용자 내부 ID
     * @throws IllegalArgumentException ID가 없거나 1보다 작은 경우
     */
    private void validUserId(Long userId) {
        if (userId == null || userId <= 0) {
            throw new IllegalArgumentException("userId는 1 이상이어야 합니다.");
        }
    }

    /**
     * 카드 ID가 파일 경로에 안전하게 포함될 수 있는 문자와 길이인지 확인한다.
     *
     * <p>영문자·숫자·밑줄·하이픈만 허용하여 상위 경로 이동과 임의 파일 접근을
     * 차단한다.</p>
     *
     * @param cardId 검사할 카드 고유 식별자
     * @throws IllegalArgumentException 카드 ID가 없거나 안전한 형식이 아닌 경우
     */
    private void validCardId(String cardId) {
        if (cardId == null || !SAFE_CARD_ID.matcher(cardId).matches()) {
            throw new IllegalArgumentException("cardId 형식이 올바르지 않습니다: " + cardId);
        }
    }

    /**
     * 다음 페이지 토큰이 고정 Dummy 파일명에 안전하게 사용할 수 있는 형식인지 확인한다.
     *
     * <p>최초 페이지는 호출자가 {@code null}로 전달하며 이 클래스가
     * {@code page-001}로 변환한다. 후속 페이지는 응답의 {@code next_page}를
     * 변경하지 않고 전달받되 {@code page-숫자 3자리} 형식만 허용한다.</p>
     *
     * @param nextPage 국내 승인내역 응답에서 받은 다음 페이지 토큰
     * @return 검증을 통과한 원본 페이지 토큰
     * @throws IllegalArgumentException 페이지 토큰이 허용된 형식이 아닌 경우
     */
    private String validNextPage(String nextPage) {
        if (!SAFE_PAGE_TOKEN.matcher(nextPage).matches()) {
            throw new IllegalArgumentException("nextPage 형식이 올바르지 않습니다: " + nextPage);
        }
        return nextPage;
    }
}
