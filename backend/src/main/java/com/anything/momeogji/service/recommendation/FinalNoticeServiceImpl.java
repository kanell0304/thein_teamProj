package com.anything.momeogji.service.recommendation;

import com.anything.momeogji.dto.recommendation.FinalNoticeResponse;
import com.anything.momeogji.dto.recommendation.GeocodedAddress;
import com.anything.momeogji.dto.recommendation.RecommendationResult;
import com.anything.momeogji.dto.recommendation.RestaurantRecommendation;
import com.anything.momeogji.dto.recommendation.VoteTallyResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
public class FinalNoticeServiceImpl implements FinalNoticeService {

    private final KakaoLocalClient kakaoLocalClient;

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

        // AI가 준 좌표/주소는 확신이 없으면 null이거나 부정확할 수 있어, 카카오 로컬 API로 한 번 더 검증한다.
        // 검증에 실패하면(주소를 못 찾거나 API 오류) AI 원본 값을 그대로 사용한다.
        String queryAddress = winner.roadAddress() != null ? winner.roadAddress() : winner.address();
        Optional<GeocodedAddress> geocoded = kakaoLocalClient.searchAddress(queryAddress);

        String roadAddress = geocoded.map(GeocodedAddress::roadAddress).filter(Objects::nonNull).orElse(winner.roadAddress());
        String address = geocoded.map(GeocodedAddress::address).filter(Objects::nonNull).orElse(winner.address());
        Double latitude = geocoded.map(GeocodedAddress::latitude).filter(Objects::nonNull).orElse(winner.latitude());
        Double longitude = geocoded.map(GeocodedAddress::longitude).filter(Objects::nonNull).orElse(winner.longitude());

        return new FinalNoticeResponse(
                winner.name(),
                roadAddress,
                address,
                latitude,
                longitude,
                recommendationResult.participantCount(),
                meetingTime
        );
    }
}
