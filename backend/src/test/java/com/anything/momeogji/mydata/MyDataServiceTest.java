package com.anything.momeogji.mydata;

import com.anything.momeogji.mydata.collection.MyDataProvider;
import com.anything.momeogji.mydata.collection.cardapproval.CardApprovalParser;
import com.anything.momeogji.mydata.collection.cardapproval.CardApprovalValidator;
import com.anything.momeogji.mydata.collection.cardlist.ConsentedCardIdSelector;
import com.anything.momeogji.mydata.collection.cardlist.CardListValidator;
import com.anything.momeogji.mydata.collection.model.CollectedUserMyData;
import com.anything.momeogji.mydata.processing.MyDataPipeline;
import com.anything.momeogji.mydata.processing.model.ProcessedUserMyData;
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
                myDataPipeline
        );
    }

    /**
     * 카드 목록 수집을 한 번 수행한 뒤 동일한 참가자 결과를 Transformer에 전달하는지 확인한다.
     */
    @Test
    void process는_수집_결과를_한_번만_가공한다() {
        given(myDataProvider.fetchCardListRawJson(1L, "0", null, 500))
                .willReturn(EMPTY_CARD_LIST_RESPONSE);
        ProcessedUserMyData processed = new ProcessedUserMyData(
                1L,
                List.of()
        );
        LocalTime meetingTime = LocalTime.of(12, 0);
        given(myDataPipeline.execute(any(CollectedUserMyData.class), eq(meetingTime), eq("FD6")))
                .willReturn(processed);

        ProcessedUserMyData result = myDataService.process(
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
        ProcessedUserMyData processed = new ProcessedUserMyData(1L, List.of());
        LocalTime meetingTime = LocalTime.of(15, 0);
        given(myDataPipeline.execute(any(CollectedUserMyData.class), eq(meetingTime), eq("CE7")))
                .willReturn(processed);

        ProcessedUserMyData result = myDataService.process(
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
        ProcessedUserMyData processed = new ProcessedUserMyData(1L, List.of());
        LocalTime meetingTime = LocalTime.of(19, 0);
        given(myDataPipeline.execute(any(CollectedUserMyData.class), eq(meetingTime), eq("FD6")))
                .willReturn(processed);

        ProcessedUserMyData result = myDataService.process(
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
}
