package com.anything.momeogji.mydata;

import com.anything.momeogji.mydata.collection.MyDataProvider;
import com.anything.momeogji.mydata.collection.cardapproval.CardApprovalParser;
import com.anything.momeogji.mydata.collection.cardapproval.CardApprovalValidator;
import com.anything.momeogji.mydata.collection.cardlist.ConsentedCardIdSelector;
import com.anything.momeogji.mydata.collection.cardlist.CardListValidator;
import com.anything.momeogji.mydata.collection.model.CollectedUserMyData;
import com.anything.momeogji.mydata.processing.MyDataPipeline;
import com.anything.momeogji.mydata.processing.model.MyDataRestaurantData;
import com.anything.momeogji.mydata.retry.MyDataExternalCallRetryExecutor;
import com.anything.momeogji.mydata.retry.MyDataRecoveryProperties;
import com.anything.momeogji.mydata.retry.RetryableMyDataExternalCallException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * MyDataService가 기존 수집 계약을 유지하면서 최종 가공 흐름을 한 번만 연결하는지 검증한다.
 */
@ExtendWith(MockitoExtension.class)
class MyDataServiceTest {

    private static final String EMPTY_CARD_LIST_RESPONSE = """
            {
              "rsp_code": "00000",
              "rsp_msg": "정상",
              "search_timestamp": "0",
              "next_page": null,
              "card_cnt": 0,
              "card_list": []
            }
            """;

    private static final String ONE_CONSENTED_CARD_RESPONSE = """
            {
              "rsp_code": "00000",
              "rsp_msg": "정상",
              "search_timestamp": "0",
              "next_page": null,
              "card_cnt": 1,
              "card_list": [
                {
                  "card_id": "001",
                  "card_num": "1234-****-****-5678",
                  "is_consent": true,
                  "card_name": "테스트 카드",
                  "card_member": "1",
                  "card_type": "01"
                }
              ]
            }
            """;

    private static final String EMPTY_APPROVAL_RESPONSE = """
            {
              "rsp_code": "00000",
              "rsp_msg": "정상",
              "next_page": null,
              "approved_cnt": 0,
              "approved_list": []
            }
            """;

    @Mock
    private MyDataProvider myDataProvider;

    @Mock
    private MyDataPipeline myDataPipeline;

    private MyDataService myDataService;

    /**
     * 실제 역직렬화·검증·파싱 컴포넌트와 Mock 경계 컴포넌트로 서비스를 구성한다.
     */
    @BeforeEach
    void setUp() {
        myDataService = new MyDataService(
                new ObjectMapper(),
                myDataProvider,
                new CardListValidator(),
                new ConsentedCardIdSelector(),
                new CardApprovalValidator(),
                new CardApprovalParser(),
                myDataPipeline,
                new MyDataExternalCallRetryExecutor(new MyDataRecoveryProperties(
                        Duration.ofMillis(1),
                        3,
                        Duration.ofSeconds(1)
                ))
        );
    }

    /**
     * 카드 목록 수집을 한 번 수행한 뒤 동일한 참가자 결과를 Transformer에 전달하는지 확인한다.
     */
    @Test
    void process는_수집_결과를_한_번만_가공한다() {
        given(myDataProvider.fetchCardListRawJson(1L, "0", null, 500))
                .willReturn(EMPTY_CARD_LIST_RESPONSE);
        List<MyDataRestaurantData> processed = List.of(
                new MyDataRestaurantData("영인성", "중식 > 중화요리")
        );
        LocalTime meetingTime = LocalTime.of(12, 0);
        given(myDataPipeline.execute(any(CollectedUserMyData.class), eq(meetingTime), eq("FD6")))
                .willReturn(processed);

        List<MyDataRestaurantData> result = myDataService.process(
                1L,
                meetingTime,
                "식사"
        );

        assertThat(result).isSameAs(processed);
        verify(myDataProvider).fetchCardListRawJson(1L, "0", null, 500);
        verify(myDataProvider, never()).fetchApprovalDomesticRawJson(
                any(),
                any(),
                any(LocalDate.class),
                any(LocalDate.class),
                any(),
                eq(500)
        );

        ArgumentCaptor<CollectedUserMyData> userMyDataCaptor =
                ArgumentCaptor.forClass(CollectedUserMyData.class);
        verify(myDataPipeline).execute(
                userMyDataCaptor.capture(),
                eq(meetingTime),
                eq("FD6")
        );
        assertThat(userMyDataCaptor.getValue().userId()).isEqualTo(1L);
        assertThat(userMyDataCaptor.getValue().approvals()).isEmpty();
    }

    /**
     * 선택 시각이 누락되면 Provider나 Transformer를 호출하기 전에 실패하는지 확인한다.
     */
    @Test
    void 선택_시각이_없으면_수집을_시작하지_않는다() {
        assertThatThrownBy(() -> myDataService.process(1L, null, "식사"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("meetingTime은 필수입니다.");

        verifyNoInteractions(myDataProvider, myDataPipeline);
    }

    /**
     * 카페 관련 목적의 앞뒤 공백을 제거하고 카페 그룹 코드로 전달하는지 확인한다.
     */
    @ParameterizedTest
    @ValueSource(strings = {"카페", "디저트"})
    void 카페와_디저트_목적은_CE7로_변환한다(String purpose) {
        given(myDataProvider.fetchCardListRawJson(1L, "0", null, 500))
                .willReturn(EMPTY_CARD_LIST_RESPONSE);
        List<MyDataRestaurantData> processed = List.of();
        LocalTime meetingTime = LocalTime.of(15, 0);
        given(myDataPipeline.execute(any(CollectedUserMyData.class), eq(meetingTime), eq("CE7")))
                .willReturn(processed);

        List<MyDataRestaurantData> result = myDataService.process(
                1L,
                meetingTime,
                "  " + purpose + "  "
        );

        assertThat(result).isSameAs(processed);
        verify(myDataPipeline).execute(any(CollectedUserMyData.class), eq(meetingTime), eq("CE7"));
    }

    /**
     * 카페 관련 목적 이외의 유효한 목적을 음식점 그룹 코드로 전달하는지 확인한다.
     */
    @ParameterizedTest
    @ValueSource(strings = {"식사", "술자리", "회식", "미팅"})
    void 일반_목적은_FD6로_변환한다(String purpose) {
        given(myDataProvider.fetchCardListRawJson(1L, "0", null, 500))
                .willReturn(EMPTY_CARD_LIST_RESPONSE);
        List<MyDataRestaurantData> processed = List.of();
        LocalTime meetingTime = LocalTime.of(19, 0);
        given(myDataPipeline.execute(any(CollectedUserMyData.class), eq(meetingTime), eq("FD6")))
                .willReturn(processed);

        List<MyDataRestaurantData> result = myDataService.process(
                1L,
                meetingTime,
                purpose
        );

        assertThat(result).isSameAs(processed);
        verify(myDataPipeline).execute(any(CollectedUserMyData.class), eq(meetingTime), eq("FD6"));
    }

    /**
     * 공백 목적은 카드 조회를 시작하기 전에 거부하는지 확인한다.
     */
    @Test
    void 목적이_공백이면_수집을_시작하지_않는다() {
        assertThatThrownBy(() -> myDataService.process(1L, LocalTime.NOON, "  "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("purpose는 필수입니다.");

        verifyNoInteractions(myDataProvider, myDataPipeline);
    }

    /**
     * 기존 collect 메서드가 Transformer와 무관하게 수집 결과를 계속 반환하는지 확인한다.
     */
    @Test
    void 기존_collect는_가공_컴포넌트를_호출하지_않는다() {
        given(myDataProvider.fetchCardListRawJson(1L, "0", null, 500))
                .willReturn(EMPTY_CARD_LIST_RESPONSE);

        CollectedUserMyData result = myDataService.collect(1L);

        assertThat(result.userId()).isEqualTo(1L);
        assertThat(result.approvals()).isEmpty();
        verifyNoInteractions(myDataPipeline);
    }

    /**
     * 카드 목록 Provider가 일시적으로 실패하면 같은 페이지 조건으로 한 번 재시도하는지 확인한다.
     */
    @Test
    void 카드목록의_일시적_외부실패는_같은_요청으로_한번_재시도한다() {
        given(myDataProvider.fetchCardListRawJson(1L, "0", null, 500))
                .willThrow(new RetryableMyDataExternalCallException(
                        "일시적 카드 목록 실패",
                        new IllegalStateException("외부 장애")
                ))
                .willReturn(EMPTY_CARD_LIST_RESPONSE);

        CollectedUserMyData result = myDataService.collect(1L);

        assertThat(result.approvals()).isEmpty();
        verify(myDataProvider, org.mockito.Mockito.times(2))
                .fetchCardListRawJson(1L, "0", null, 500);
    }

    /**
     * 국내 승인내역 외부 요청의 기간·카드·페이지 조건이 재시도에서도 바뀌지 않는지 확인한다.
     */
    @Test
    void 승인내역의_일시적_외부실패는_같은_요청조건으로_한번_재시도한다() {
        given(myDataProvider.fetchCardListRawJson(1L, "0", null, 500))
                .willReturn(ONE_CONSENTED_CARD_RESPONSE);
        given(myDataProvider.fetchApprovalDomesticRawJson(
                eq(1L),
                eq("001"),
                any(LocalDate.class),
                any(LocalDate.class),
                org.mockito.ArgumentMatchers.isNull(),
                eq(500)
        ))
                .willThrow(new RetryableMyDataExternalCallException(
                        "일시적 승인내역 실패",
                        new IllegalStateException("외부 장애")
                ))
                .willReturn(EMPTY_APPROVAL_RESPONSE);

        CollectedUserMyData result = myDataService.collect(1L);

        ArgumentCaptor<LocalDate> fromDateCaptor = ArgumentCaptor.forClass(LocalDate.class);
        ArgumentCaptor<LocalDate> toDateCaptor = ArgumentCaptor.forClass(LocalDate.class);
        verify(myDataProvider, org.mockito.Mockito.times(2))
                .fetchApprovalDomesticRawJson(
                        eq(1L),
                        eq("001"),
                        fromDateCaptor.capture(),
                        toDateCaptor.capture(),
                        org.mockito.ArgumentMatchers.isNull(),
                        eq(500)
                );

        assertThat(result.approvals()).isEmpty();
        assertThat(fromDateCaptor.getAllValues()).hasSize(2).allMatch(
                fromDate -> fromDate.equals(fromDateCaptor.getAllValues().getFirst())
        );
        assertThat(toDateCaptor.getAllValues()).hasSize(2).allMatch(
                toDate -> toDate.equals(toDateCaptor.getAllValues().getFirst())
        );
    }

    /**
     * 결정적인 Provider 오류는 재시도하지 않고 최초 예외를 즉시 전달하는지 확인한다.
     */
    @Test
    void 재시도대상이_아닌_Provider오류는_즉시_실패한다() {
        given(myDataProvider.fetchCardListRawJson(1L, "0", null, 500))
                .willThrow(new IllegalStateException("Dummy 파일 누락"));

        assertThatThrownBy(() -> myDataService.collect(1L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Dummy 파일 누락");

        verify(myDataProvider).fetchCardListRawJson(1L, "0", null, 500);
    }
}
