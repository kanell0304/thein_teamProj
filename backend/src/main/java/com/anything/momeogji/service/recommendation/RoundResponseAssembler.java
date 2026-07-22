package com.anything.momeogji.service.recommendation;

import com.anything.momeogji.dto.recommendation.CandidateSummary;
import com.anything.momeogji.dto.recommendation.RoundResponse;
import com.anything.momeogji.entity.recommendation.RecommendationRound;
import com.anything.momeogji.entity.recommendation.Restaurant;
import com.anything.momeogji.entity.recommendation.RoundCandidate;
import com.anything.momeogji.repository.RoundCandidateRepository;
import com.anything.momeogji.repository.VoteRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Comparator;

/** RecommendationRound + 현재 득표수를 합쳐 RoundResponse를 조립한다. 회차 생성/투표/투표취소/조회 응답에서 공통으로 쓴다. */
@Component
@RequiredArgsConstructor
class RoundResponseAssembler {

    private final RoundCandidateRepository roundCandidateRepository;
    private final VoteRepository voteRepository;

    RoundResponse assemble(RecommendationRound round) {
        var candidates = roundCandidateRepository.findByRoundId(round.getId()).stream()
                .sorted(Comparator.comparingInt(RoundCandidate::getRankNo))
                .map(this::toSummary)
                .toList();

        return new RoundResponse(
                round.getMeetup().getId(),
                round.getId(),
                round.getRoundNo(),
                round.getParticipantCount(),
                (int) voteRepository.countDistinctVotersByRoundId(round.getId()),
                candidates
        );
    }

    private CandidateSummary toSummary(RoundCandidate candidate) {
        Restaurant restaurant = candidate.getRestaurant();
        long voteCount = voteRepository.countByRoundCandidateId(candidate.getId());
        var voterIds = voteRepository.findByRoundCandidateId(candidate.getId()).stream()
                .map(vote -> vote.getMeetupParticipant().getUser().getId())
                .distinct()
                .toList();
        boolean recommendAgain = RecommendationRoundServiceImpl.RECOMMEND_AGAIN_PLACE_ID
                .equals(restaurant.getKakaoPlaceId());

        return new CandidateSummary(
                candidate.getId(),
                candidate.getRankNo(),
                restaurant.getName(),
                restaurant.getCategory(),
                restaurant.getRoadAddress(),
                restaurant.getAddress(),
                restaurant.getLatitude() == null ? null : restaurant.getLatitude().doubleValue(),
                restaurant.getLongitude() == null ? null : restaurant.getLongitude().doubleValue(),
                candidate.getReason(),
                candidate.getImageUrl(),
                voteCount,
                recommendAgain ? "RECOMMEND_AGAIN" : "RESTAURANT",
                voterIds
        );
    }
}
