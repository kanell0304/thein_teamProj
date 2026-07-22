package com.anything.momeogji.service.recommendation;

import com.anything.momeogji.dto.recommendation.PersonalOptionRequest;
import com.anything.momeogji.entity.recommendation.ConsentStatus;
import com.anything.momeogji.entity.recommendation.MydataConsent;
import com.anything.momeogji.entity.recommendation.ParticipantPreference;
import com.anything.momeogji.entity.recommendation.ProcessingStatus;
import com.anything.momeogji.repository.MydataConsentRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 마이데이터 활용에 동의하고 처리까지 끝난 참여자의 카페(CE7) 방문 비율이 음식점(FD6)보다 높으면,
 * 그 참여자의 preferredCategories에 "카페/디저트"를 한 표 추가한다.
 * RecommendationConditionAggregator는 이 리스트를 그대로 빈도 집계하므로, 이 클래스 하나만으로
 * 카테고리 검색·AI 프롬프트에 자연스럽게 가중치가 반영되고 다른 코드는 건드릴 필요가 없다.
 */
@Component
@RequiredArgsConstructor
public class MyDataCategoryEnricher {

    private static final String CAFE_DESSERT_CATEGORY = "카페/디저트";

    private final MydataConsentRepository mydataConsentRepository;
    private final ObjectMapper objectMapper;

    public List<PersonalOptionRequest> enrich(List<ParticipantPreference> preferences, List<PersonalOptionRequest> baseOptions) {
        Map<Long, Long> meetupParticipantIdByMemberId = preferences.stream()
                .collect(Collectors.toMap(
                        preference -> preference.getMeetupParticipant().getUser().getId(),
                        preference -> preference.getMeetupParticipant().getId(),
                        (first, second) -> first));

        return baseOptions.stream()
                .map(option -> enrichOne(option, meetupParticipantIdByMemberId.get(option.participantId())))
                .toList();
    }

    private PersonalOptionRequest enrichOne(PersonalOptionRequest option, Long meetupParticipantId) {
        if (meetupParticipantId == null || option.preferredCategories().contains(CAFE_DESSERT_CATEGORY)) {
            return option;
        }

        boolean prefersCafe = mydataConsentRepository.findByMeetupParticipantId(meetupParticipantId)
                .filter(this::isUsableCafeSignal)
                .map(this::readSummary)
                .map(summary -> summary.ce7Count() > summary.fd6Count())
                .orElse(false);

        if (!prefersCafe) {
            return option;
        }

        List<String> enrichedCategories = new ArrayList<>(option.preferredCategories());
        enrichedCategories.add(CAFE_DESSERT_CATEGORY);

        return new PersonalOptionRequest(option.participantId(), option.walkMinutes(), enrichedCategories,
                option.budgetLimit(), option.parkingNeeded(), option.excludedFoods(), option.atmosphere());
    }

    private boolean isUsableCafeSignal(MydataConsent consent) {
        return consent.getConsentStatus() == ConsentStatus.AGREED
                && consent.getProcessingStatus() == ProcessingStatus.COMPLETED
                && consent.getProcessedResult() != null;
    }

    private CategoryUsageSummary readSummary(MydataConsent consent) {
        try {
            return objectMapper.readValue(consent.getProcessedResult(), CategoryUsageSummary.class);
        } catch (JsonProcessingException e) {
            return new CategoryUsageSummary(0, 0);
        }
    }
}
