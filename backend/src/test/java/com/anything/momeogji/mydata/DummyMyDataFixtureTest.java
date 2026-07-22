package com.anything.momeogji.mydata;

import com.anything.momeogji.mydata.cardapproval.CardApprovalResponse;
import com.anything.momeogji.mydata.cardapproval.CardApprovalValidator;
import com.anything.momeogji.mydata.cardlist.CardListResponse;
import com.anything.momeogji.mydata.cardlist.CardListValidator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 실행용 MyData Dummy 파일의 API 계약, 페이지 연결, 지역 배정을 검증한다.
 *
 * <p>지역 검증은 외부 API를 다시 호출하지 않고 2026-07-22 카카오 장소 조회로
 * 확정한 지점명 집합과 fixture의 배정 건수를 비교한다.</p>
 */
class DummyMyDataFixtureTest {

    private static final DateTimeFormatter DATE_TIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private static final LocalDate FROM_DATE = LocalDate.of(2025, 7, 22);
    private static final LocalDate TO_DATE = LocalDate.of(2026, 7, 22);

    private static final Set<String> SINNONHYEON_MERCHANTS = Set.of(
            "버거킹 신논현역점", "삼송빵집 신논현점", "누들톡 신논현역점", "상무초밥 강남점",
            "원조원주추어탕 강남본점", "마리짱 신논현역점", "청담쭈꾸미 신논현 본점", "메리가든",
            "커피빈 신논현역점", "올리버브라운 신논현점", "GS25 S9신논현역점", "신논현약국"
    );
    private static final Set<String> YANGJAE_MERCHANTS = Set.of(
            "버거킹 양재점", "금강수림", "마성떡볶이 양재역점", "BGT호두단팥빵 양재역점",
            "곱창왕김형제 양재본점", "찌개집1979 양재역점", "지세포세꼬시", "석화촌",
            "하우 양재역점", "메가MGC커피 양재역지하상가점", "GS25 S양재역점", "양재미소약국"
    );
    private static final Set<String> YONGSAN_MERCHANTS = Set.of(
            "삼진어묵 용산역점", "용우동 용산역점", "아그라 용산아이파크몰점",
            "소녀방앗간 용산아이파크몰점", "혼고집 용산아이파크몰점", "낙원테산도 용산아이파크몰점",
            "멘야서울 용산아이파크몰점", "연돈볼카츠 이마트용산점", "카페 뮬리노 용산아이파크몰점",
            "베르그 아이파크몰 용산점", "CU 용산아이파크몰점", "스타약국"
    );
    private static final Set<String> GANGNAM_MERCHANTS = Set.of(
            "포490베트남쌀국수 강남점", "사이공본가 강남역점", "명인만두 강남역지하상가점",
            "브릭샌드 강남역점", "강다짐 강남역지하상가점", "삼형제김밥 강남역점",
            "로봇김밥 신분당선강남역점", "킹즈 치아바타", "빽다방 강남역지하도점", "공차 강남역점",
            "GS25 S강남역1호점", "365강남역약국"
    );
    private static final Set<String> YEOUIDO_MERCHANTS = Set.of(
            "디트로이트1달러피자", "육랩 여의도점", "브로트아트 여의도역2호점", "리나스 여의도역점",
            "카이저호프", "우아 여의도점", "로비스불닭바베큐", "유일양꼬치", "공차 여의도역사점",
            "브루다커피 여의도역점", "세븐일레븐 S여의도역점", "여의도약국"
    );

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final DummyMyDataProvider provider = new DummyMyDataProvider();
    private final CardListValidator cardListValidator = new CardListValidator();
    private final CardApprovalValidator approvalValidator = new CardApprovalValidator();

    /**
     * 세 사용자의 카드 목록이 검증을 통과하고 카드 유형·동의 상태가 계획과 일치한다.
     */
    @Test
    void 카드_목록은_유형_오름차순이며_동의_상태가_계획과_일치한다() {
        CardListResponse user01 = cardList(1L);
        CardListResponse user02 = cardList(2L);
        CardListResponse user03 = cardList(3L);

        assertCardTypesSorted(user01);
        assertCardTypesSorted(user02);
        assertCardTypesSorted(user03);
        assertThat(user01.searchTimestamp()).isEqualTo("20260722090000");
        assertThat(user02.searchTimestamp()).isEqualTo("20260722090000");
        assertThat(user03.searchTimestamp()).isEqualTo("20260722090000");
        assertThat(user01.cards()).extracting(CardListResponse.CardItem::consented)
                .containsExactly(true, true, false);
        assertThat(user02.cards()).extracting(CardListResponse.CardItem::consented)
                .containsOnly(true);
        assertThat(user03.cards()).extracting(CardListResponse.CardItem::consented)
                .containsOnly(true);
        assertThat(List.of(user01, user02, user03))
                .flatExtracting(CardListResponse::cards)
                .allSatisfy(card -> {
                    assertThat(card.memberCode()).isEqualTo("1");
                    assertThat(card.maskedCardNumber()).contains("******");
                });
    }

    /**
     * 최초 페이지와 후속 페이지 토큰이 파일명에 연결되고 잘못된 토큰은 거부된다.
     */
    @Test
    void 승인내역_페이지_토큰을_파일_선택에_사용한다() {
        CardApprovalResponse first = approvalPage(2L, "001", null);
        CardApprovalResponse second = approvalPage(2L, "001", first.nextPage());
        CardApprovalResponse third = approvalPage(2L, "001", second.nextPage());

        assertThat(first.approvalCount()).isEqualTo(24);
        assertThat(first.nextPage()).isEqualTo("page-002");
        assertThat(second.approvalCount()).isEqualTo(24);
        assertThat(second.nextPage()).isEqualTo("page-003");
        assertThat(third.approvalCount()).isEqualTo(20);
        assertThat(third.nextPage()).isNull();

        assertThatThrownBy(() -> provider.fetchDomesticApprovals(
                2L, "001", FROM_DATE, TO_DATE, "page-2", 500
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("nextPage 형식");
        assertThatThrownBy(() -> provider.fetchDomesticApprovals(
                2L, "001", FROM_DATE, TO_DATE, "../page-002", 500
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("nextPage 형식");
        assertThatThrownBy(() -> provider.fetchDomesticApprovals(
                1L, "003", FROM_DATE, TO_DATE, null, 500
        )).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("approval-domestic-003-page-001.json");
    }

    /**
     * user-02의 상태·할부·날짜 범위·사용일시 정렬 조건을 세 페이지 전체에서 확인한다.
     */
    @Test
    void user02_신용카드_경계_사례와_정렬이_계획과_일치한다() {
        List<CardApprovalResponse> pages = List.of(
                approvalPage(2L, "001", null),
                approvalPage(2L, "001", "page-002"),
                approvalPage(2L, "001", "page-003")
        );
        List<CardApprovalResponse.ApprovalItem> approvals = pages.stream()
                .flatMap(page -> page.approvals().stream())
                .toList();

        assertThat(approvals).hasSize(68);
        assertThat(approvals.stream().collect(Collectors.groupingBy(
                CardApprovalResponse.ApprovalItem::statusCode,
                Collectors.counting()
        ))).containsExactlyInAnyOrderEntriesOf(
                Map.of("01", 60L, "02", 3L, "03", 2L, "04", 3L)
        );
        assertThat(approvals).extracting(CardApprovalResponse.ApprovalItem::payTypeCode)
                .containsOnly("01");
        assertThat(approvals)
                .filteredOn(item -> item.totalInstallmentCount() != null)
                .hasSize(6)
                .extracting(CardApprovalResponse.ApprovalItem::totalInstallmentCount)
                .containsExactlyInAnyOrder(3, 3, 6, 6, 12, 12);
        assertThat(approvals).filteredOn(item -> Set.of("02", "03").contains(item.statusCode()))
                .allSatisfy(item -> assertThat(item.transactionDateTime()).isNotNull());
        assertThat(approvals).filteredOn(item -> "03".equals(item.statusCode()))
                .allSatisfy(item -> assertThat(item.modifiedAmount()).isNotNull());
        assertThat(approvals).extracting(CardApprovalResponse.ApprovalItem::approvalNumber)
                .doesNotHaveDuplicates();

        assertEffectiveDateRange(pages.get(0), "20260721", "20260628");
        assertEffectiveDateRange(pages.get(1), "20260627", "20260604");
        assertEffectiveDateRange(pages.get(2), "20260603", "20260515");
        pages.forEach(this::assertEffectiveTimeDescending);
        assertThat(effectiveDateTime(pages.get(0).approvals().getLast()))
                .isAfter(effectiveDateTime(pages.get(1).approvals().getFirst()));
        assertThat(effectiveDateTime(pages.get(1).approvals().getLast()))
                .isAfter(effectiveDateTime(pages.get(2).approvals().getFirst()));
    }

    /**
     * user-03 체크카드의 빈 응답, 시간대 경계, 취소·무승인매입 사례를 확인한다.
     */
    @Test
    void user03_체크카드_경계값과_빈_카드가_계획과_일치한다() {
        CardApprovalResponse card001 = approvalPage(3L, "001", null);
        CardApprovalResponse card002 = approvalPage(3L, "002", null);
        CardApprovalResponse card003 = approvalPage(3L, "003", null);
        CardApprovalResponse card004 = approvalPage(3L, "004", null);
        List<CardApprovalResponse.ApprovalItem> approvals = new ArrayList<>();
        approvals.addAll(card001.approvals());
        approvals.addAll(card002.approvals());
        approvals.addAll(card004.approvals());

        assertThat(card001.approvalCount()).isEqualTo(12);
        assertThat(card002.approvalCount()).isEqualTo(12);
        assertThat(card003.approvalCount()).isZero();
        assertThat(card003.approvals()).isEmpty();
        assertThat(card004.approvalCount()).isEqualTo(12);
        assertThat(approvals).extracting(CardApprovalResponse.ApprovalItem::payTypeCode)
                .containsOnly("02");
        assertThat(approvals).extracting(CardApprovalResponse.ApprovalItem::totalInstallmentCount)
                .containsOnlyNulls();
        assertThat(card002.approvals())
                .filteredOn(item -> "02".equals(item.statusCode()))
                .hasSize(1);
        assertThat(card004.approvals())
                .filteredOn(item -> "04".equals(item.statusCode()))
                .hasSize(1);

        assertThat(card001.approvals()).extracting(this::approvedTime)
                .contains(LocalTime.of(2, 0), LocalTime.of(9, 59));
        assertThat(card002.approvals()).extracting(this::approvedTime)
                .contains(LocalTime.of(10, 0), LocalTime.of(15, 59));
        assertThat(card004.approvals()).extracting(this::approvedTime)
                .contains(LocalTime.of(16, 0), LocalTime.of(1, 59));

        Set<String> card002Merchants = card002.approvals().stream()
                .map(CardApprovalResponse.ApprovalItem::merchantName)
                .collect(Collectors.toSet());
        Set<String> card004Merchants = card004.approvals().stream()
                .map(CardApprovalResponse.ApprovalItem::merchantName)
                .collect(Collectors.toSet());
        assertThat(card002Merchants).containsAnyElementsOf(card004Merchants);
        assertThat(approvals).extracting(CardApprovalResponse.ApprovalItem::approvalNumber)
                .doesNotHaveDuplicates();
    }

    /**
     * 확정된 카카오 지점명 집합을 기준으로 신규 fixture의 지역별 건수를 검사한다.
     */
    @Test
    void 신규_가맹점은_확정된_지역_지점명과_건수만_사용한다() {
        assertRegionCounts(approvalPage(1L, "002", null), Map.of("양재역", 24));
        assertRegionCounts(
                approvalPage(2L, "001", null),
                Map.of("신논현역", 18, "용산역", 6)
        );
        assertRegionCounts(
                approvalPage(2L, "001", "page-002"),
                Map.of("신논현역", 12, "용산역", 12)
        );
        assertRegionCounts(
                approvalPage(2L, "001", "page-003"),
                Map.of("신논현역", 5, "용산역", 15)
        );
        assertRegionCounts(approvalPage(3L, "001", null), Map.of("강남역", 12));
        assertRegionCounts(approvalPage(3L, "002", null), Map.of("여의도역", 12));
        assertRegionCounts(approvalPage(3L, "004", null), Map.of("여의도역", 12));
    }

    private CardListResponse cardList(Long userId) {
        try {
            CardListResponse response = objectMapper.readValue(
                    provider.fetchCardList(userId, "0", null, 500),
                    CardListResponse.class
            );
            cardListValidator.validate(response);
            return response;
        } catch (JsonProcessingException exception) {
            throw new AssertionError("카드 목록 fixture 역직렬화 실패", exception);
        }
    }

    private CardApprovalResponse approvalPage(Long userId, String cardId, String nextPage) {
        try {
            CardApprovalResponse response = objectMapper.readValue(
                    provider.fetchDomesticApprovals(
                            userId, cardId, FROM_DATE, TO_DATE, nextPage, 500
                    ),
                    CardApprovalResponse.class
            );
            approvalValidator.validate(response);
            return response;
        } catch (JsonProcessingException exception) {
            throw new AssertionError("승인내역 fixture 역직렬화 실패", exception);
        }
    }

    private void assertCardTypesSorted(CardListResponse response) {
        List<String> types = response.cards().stream()
                .map(CardListResponse.CardItem::typeCode)
                .toList();
        assertThat(types).isSortedAccordingTo(Comparator.naturalOrder());
    }

    private void assertEffectiveDateRange(
            CardApprovalResponse response,
            String expectedNewestDate,
            String expectedOldestDate
    ) {
        assertThat(effectiveDateTime(response.approvals().getFirst()).toLocalDate())
                .isEqualTo(LocalDate.parse(expectedNewestDate, DateTimeFormatter.BASIC_ISO_DATE));
        assertThat(effectiveDateTime(response.approvals().getLast()).toLocalDate())
                .isEqualTo(LocalDate.parse(expectedOldestDate, DateTimeFormatter.BASIC_ISO_DATE));
    }

    private void assertEffectiveTimeDescending(CardApprovalResponse response) {
        List<LocalDateTime> effectiveTimes = response.approvals().stream()
                .map(this::effectiveDateTime)
                .toList();
        assertThat(effectiveTimes).isSortedAccordingTo(Comparator.reverseOrder());
    }

    private LocalDateTime effectiveDateTime(CardApprovalResponse.ApprovalItem item) {
        String value = Set.of("02", "03").contains(item.statusCode())
                ? item.transactionDateTime()
                : item.approvedDateTime();
        return LocalDateTime.parse(value, DATE_TIME_FORMATTER);
    }

    private LocalTime approvedTime(CardApprovalResponse.ApprovalItem item) {
        return LocalDateTime.parse(item.approvedDateTime(), DATE_TIME_FORMATTER).toLocalTime();
    }

    private void assertRegionCounts(
            CardApprovalResponse response,
            Map<String, Integer> expectedCounts
    ) {
        Map<String, Set<String>> merchantsByRegion = Map.of(
                "신논현역", SINNONHYEON_MERCHANTS,
                "양재역", YANGJAE_MERCHANTS,
                "용산역", YONGSAN_MERCHANTS,
                "강남역", GANGNAM_MERCHANTS,
                "여의도역", YEOUIDO_MERCHANTS
        );
        Map<String, Long> actualCounts = response.approvals().stream()
                .map(item -> regionOf(item.merchantName(), merchantsByRegion))
                .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));

        Map<String, Long> expectedLongCounts = expectedCounts.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, entry -> entry.getValue().longValue()));
        assertThat(actualCounts).containsExactlyInAnyOrderEntriesOf(expectedLongCounts);
        assertThat(response.approvals())
                .allSatisfy(item -> assertThat(item.merchantRegistrationNumber())
                        .matches("000-00-\\d{5}"));
    }

    private String regionOf(String merchantName, Map<String, Set<String>> merchantsByRegion) {
        Set<String> matchedRegions = merchantsByRegion.entrySet().stream()
                .filter(entry -> entry.getValue().contains(merchantName))
                .map(Map.Entry::getKey)
                .collect(Collectors.toCollection(HashSet::new));
        assertThat(matchedRegions)
                .as("가맹점 지역은 하나로 식별되어야 함: %s", merchantName)
                .hasSize(1);
        return matchedRegions.iterator().next();
    }
}
