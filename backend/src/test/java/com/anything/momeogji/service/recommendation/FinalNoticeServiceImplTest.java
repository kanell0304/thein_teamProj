package com.anything.momeogji.service.recommendation;

import com.anything.momeogji.dto.recommendation.FinalNoticeResponse;
import com.anything.momeogji.dto.recommendation.GeocodedAddress;
import com.anything.momeogji.dto.recommendation.RecommendationResult;
import com.anything.momeogji.dto.recommendation.RestaurantRecommendation;
import com.anything.momeogji.dto.recommendation.Tier;
import com.anything.momeogji.dto.recommendation.VoteTallyResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class FinalNoticeServiceImplTest {

    @Mock
    private KakaoLocalClient kakaoLocalClient;

    private FinalNoticeServiceImpl finalNoticeService;

    @BeforeEach
    void setUp() {
        finalNoticeService = new FinalNoticeServiceImpl(kakaoLocalClient);
    }

    @Test
    void 카카오_검증에_성공하면_보정된_주소_좌표를_사용한다() {
        given(kakaoLocalClient.searchAddress("서울 강남구 테헤란로 1"))
                .willReturn(Optional.of(new GeocodedAddress("서울 강남구 테헤란로 1(보정)", "서울 강남구 역삼동 1-1(보정)", 37.5001, 127.0281)));

        RecommendationResult recommendationResult = new RecommendationResult(
                4,
                List.of(new RestaurantRecommendation(1, Tier.PRIMARY, "모먹지 김밥천국", "한식",
                        "서울 강남구 테헤란로 1", "서울 강남구 역삼동 1", 37.499, 127.028, "이유")),
                List.of()
        );
        VoteTallyResult tallyResult = new VoteTallyResult("모먹지 김밥천국", false, Map.of("모먹지 김밥천국", 4L));
        LocalDateTime meetingTime = LocalDateTime.of(2026, 7, 20, 12, 0);

        FinalNoticeResponse notice = finalNoticeService.buildFinalNotice(recommendationResult, tallyResult, meetingTime);

        assertThat(notice.restaurantName()).isEqualTo("모먹지 김밥천국");
        assertThat(notice.participantCount()).isEqualTo(4);
        assertThat(notice.meetingTime()).isEqualTo(meetingTime);
        assertThat(notice.roadAddress()).isEqualTo("서울 강남구 테헤란로 1(보정)");
        assertThat(notice.latitude()).isEqualTo(37.5001);
        assertThat(notice.longitude()).isEqualTo(127.0281);
    }

    @Test
    void 카카오_검증에_실패하면_AI가_준_원본_좌표로_폴백한다() {
        given(kakaoLocalClient.searchAddress(any())).willReturn(Optional.empty());

        RecommendationResult recommendationResult = new RecommendationResult(
                2,
                List.of(new RestaurantRecommendation(1, Tier.PRIMARY, "모먹지 파스타", "양식",
                        null, "서울 마포구 어딘가", null, null, "이유")),
                List.of()
        );
        VoteTallyResult tallyResult = new VoteTallyResult("모먹지 파스타", false, Map.of("모먹지 파스타", 2L));
        LocalDateTime meetingTime = LocalDateTime.of(2026, 7, 20, 12, 0);

        FinalNoticeResponse notice = finalNoticeService.buildFinalNotice(recommendationResult, tallyResult, meetingTime);

        assertThat(notice.address()).isEqualTo("서울 마포구 어딘가");
        assertThat(notice.roadAddress()).isNull();
        assertThat(notice.latitude()).isNull();
        assertThat(notice.longitude()).isNull();
    }

    @Test
    void 투표_결과에_해당하는_음식점이_추천목록에_없으면_카카오_호출없이_예외() {
        RecommendationResult recommendationResult = new RecommendationResult(2, List.of(), List.of());
        VoteTallyResult tallyResult = new VoteTallyResult("존재하지않음", false, Map.of());

        assertThatThrownBy(() ->
                finalNoticeService.buildFinalNotice(recommendationResult, tallyResult, LocalDateTime.now()))
                .isInstanceOf(IllegalArgumentException.class);

        verify(kakaoLocalClient, never()).searchAddress(any());
    }
}
