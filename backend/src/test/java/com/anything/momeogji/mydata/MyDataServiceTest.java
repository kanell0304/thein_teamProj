package com.anything.momeogji.mydata;

import com.anything.momeogji.mydata.cardapproval.CardApprovalParser;
import com.anything.momeogji.mydata.cardapproval.CardApprovalValidator;
import com.anything.momeogji.mydata.cardlist.CardListParser;
import com.anything.momeogji.mydata.cardlist.CardListValidator;
import com.anything.momeogji.mydata.model.UserMyData;
import com.anything.momeogji.mydata.transform.MyDataTransformer;
import com.anything.momeogji.mydata.transform.model.TransformedUserMyData;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
    private MyDataTransformer myDataTransformer;

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
                new CardListParser(),
                new CardApprovalValidator(),
                new CardApprovalParser(),
                myDataTransformer
        );
    }

    /**
     * 카드 목록 수집을 한 번 수행한 뒤 동일한 참가자 결과를 Transformer에 전달하는지 확인한다.
     */
    @Test
    void process는_수집_결과를_한_번만_가공한다() {
        given(myDataProvider.fetchCardList(1L, "0", null, 500))
                .willReturn(EMPTY_CARD_LIST_RESPONSE);
        TransformedUserMyData transformed = new TransformedUserMyData(
                1L,
                List.of()
        );
        LocalTime meetingTime = LocalTime.of(12, 0);
        given(myDataTransformer.transform(any(UserMyData.class), eq(meetingTime)))
                .willReturn(transformed);

        TransformedUserMyData result = myDataService.process(
                1L,
                meetingTime
        );

        assertThat(result).isSameAs(transformed);
        verify(myDataProvider).fetchCardList(1L, "0", null, 500);
        verify(myDataProvider, never()).fetchDomesticApprovals(
                any(),
                any(),
                any(LocalDate.class),
                any(LocalDate.class),
                any(),
                eq(500)
        );

        ArgumentCaptor<UserMyData> userMyDataCaptor =
                ArgumentCaptor.forClass(UserMyData.class);
        verify(myDataTransformer).transform(
                userMyDataCaptor.capture(),
                eq(meetingTime)
        );
        assertThat(userMyDataCaptor.getValue().userId()).isEqualTo(1L);
        assertThat(userMyDataCaptor.getValue().approvals()).isEmpty();
    }

    /**
     * 선택 시각이 누락되면 Provider나 Transformer를 호출하기 전에 실패하는지 확인한다.
     */
    @Test
    void 선택_시각이_없으면_수집을_시작하지_않는다() {
        assertThatThrownBy(() -> myDataService.process(1L, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("meetingTime은 필수입니다.");

        verifyNoInteractions(myDataProvider, myDataTransformer);
    }

    /**
     * 기존 collect 메서드가 Transformer와 무관하게 수집 결과를 계속 반환하는지 확인한다.
     */
    @Test
    void 기존_collect는_가공_컴포넌트를_호출하지_않는다() {
        given(myDataProvider.fetchCardList(1L, "0", null, 500))
                .willReturn(EMPTY_CARD_LIST_RESPONSE);

        UserMyData result = myDataService.collect(1L);

        assertThat(result.userId()).isEqualTo(1L);
        assertThat(result.approvals()).isEmpty();
        verifyNoInteractions(myDataTransformer);
    }
}
