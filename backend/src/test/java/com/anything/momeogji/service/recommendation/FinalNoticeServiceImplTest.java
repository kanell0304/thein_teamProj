package com.anything.momeogji.service.recommendation;

import com.anything.momeogji.dto.recommendation.FinalNoticeResponse;
import com.anything.momeogji.dto.recommendation.RecommendationResult;
import com.anything.momeogji.dto.recommendation.RestaurantRecommendation;
import com.anything.momeogji.dto.recommendation.Tier;
import com.anything.momeogji.dto.recommendation.VoteTallyResult;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FinalNoticeServiceImplTest {

    private final FinalNoticeServiceImpl finalNoticeService = new FinalNoticeServiceImpl();

    @Test
    void 투표에서_이긴_음식점_정보로_최종공지를_만든다() {
        RecommendationResult recommendationResult = new RecommendationResult(
                4,
                List.of(new RestaurantRecommendation("c1", 1, Tier.PRIMARY, "모먹지 김밥천국", "한식",
                        "서울 강남구 테헤란로 1", "서울 강남구 역삼동 1", 37.499, 127.028, "이유")),
                List.of()
        );
        VoteTallyResult tallyResult = new VoteTallyResult("모먹지 김밥천국", false, Map.of("모먹지 김밥천국", 4L));
        LocalDateTime meetingTime = LocalDateTime.of(2026, 7, 20, 12, 0);

        FinalNoticeResponse notice = finalNoticeService.buildFinalNotice(recommendationResult, tallyResult, meetingTime);

        assertThat(notice.restaurantName()).isEqualTo("모먹지 김밥천국");
        assertThat(notice.participantCount()).isEqualTo(4);
        assertThat(notice.meetingTime()).isEqualTo(meetingTime);
        assertThat(notice.latitude()).isEqualTo(37.499);
        assertThat(notice.roadAddress()).isEqualTo("서울 강남구 테헤란로 1");
    }

    @Test
    void 투표_결과에_해당하는_음식점이_추천목록에_없으면_예외() {
        RecommendationResult recommendationResult = new RecommendationResult(2, List.of(), List.of());
        VoteTallyResult tallyResult = new VoteTallyResult("존재하지않음", false, Map.of());

        assertThatThrownBy(() ->
                finalNoticeService.buildFinalNotice(recommendationResult, tallyResult, LocalDateTime.now()))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
