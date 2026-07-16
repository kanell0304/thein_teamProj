package com.anything.mydataapi;

import com.anything.momeogji.mydata.MyDataProvider;
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
    public String fetchDomesticApprovals(Long participantId, String cardId, LocalDate fromDate,
                                         LocalDate toDate, String nextPage, int limit) {
        validUserId(participantId);
        validCardId(cardId);

        String resourcePath = getDirectory(participantId)
                + "/approval-domestic-" + cardId + ".json";
        return getDummyFile(resourcePath);
    }

    /**
     * 참가자 ID를 classpath의 {@code user-숫자} 형식 Dummy 디렉터리 경로로 변환한다.
     *
     * @param participantId 검증이 끝난 참가자 내부 ID
     * @return {@code mydata/dummy/user-01} 형식의 classpath 상대 경로
     */
    private String getDirectory(Long participantId) {
        return DUMMY_BASE_PATH + "/user-%02d".formatted(participantId);
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
     * 참가자 ID가 Dummy 디렉터리 이름으로 사용할 수 있는 양수인지 확인한다.
     *
     * @param participantId 검사할 참가자 내부 ID
     * @throws IllegalArgumentException ID가 없거나 1보다 작은 경우
     */
    private void validUserId(Long participantId) {
        if (participantId == null || participantId <= 0) {
            throw new IllegalArgumentException("participantId는 1 이상이어야 합니다.");
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
}
