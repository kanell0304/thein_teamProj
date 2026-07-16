package com.anything.momeogji.service.recommendation;

import com.anything.momeogji.dto.recommendation.FinalNoticeResponse;
import com.anything.momeogji.dto.recommendation.RecommendationResult;
import com.anything.momeogji.dto.recommendation.RestaurantRecommendation;
import com.anything.momeogji.dto.recommendation.VoteTallyResult;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.stream.Stream;

// 추천 단계에서 이미 카카오 실검색 후보로만 채워진 데이터이므로, 여기서는 별도 좌표 재검증 없이 그대로 사용한다.
@Service
public class FinalNoticeServiceImpl implements FinalNoticeService {

    @Override
    public FinalNoticeResponse buildFinalNotice(RecommendationResult recommendationResult,
                                                 VoteTallyResult tallyResult,
                                                 LocalDateTime meetingTime) {
        RestaurantRecommendation winner = Stream.concat(
                        recommendationResult.primaryRecommendations().stream(),
                        recommendationResult.extraRecommendations().stream())
                .filter(candidate -> candidate.name().equals(tallyResult.winnerRestaurantName()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "투표 결과에 해당하는 음식점을 추천 목록에서 찾을 수 없습니다: " + tallyResult.winnerRestaurantName()));

        return new FinalNoticeResponse(
                winner.name(),
                winner.roadAddress(),
                winner.address(),
                winner.latitude(),
                winner.longitude(),
                recommendationResult.participantCount(),
                meetingTime
        );
    }
}
