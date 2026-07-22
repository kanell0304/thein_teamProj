package com.anything.momeogji.service.recommendation;

import com.anything.momeogji.dto.recommendation.PersonalOptionRequest;
import com.anything.momeogji.entity.recommendation.MeetupParticipant;
import com.anything.momeogji.entity.recommendation.ParticipantPreference;
import com.anything.momeogji.repository.ParticipantPreferenceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** 참여자 개인 선호를 저장하거나(최초 제출) 갱신한다(재추천 등으로 다시 제출). */
@Component
@RequiredArgsConstructor
class ParticipantPreferenceUpserter {

    private final ParticipantPreferenceRepository participantPreferenceRepository;

    void upsert(MeetupParticipant participant, PersonalOptionRequest option) {
        ParticipantPreference existing = participantPreferenceRepository
                .findByMeetupParticipantId(participant.getId())
                .orElse(null);

        if (existing == null) {
            participantPreferenceRepository.save(ParticipantPreference.builder()
                    .meetupParticipant(participant)
                    .walkMinutes(option.walkMinutes())
                    .preferredCategories(option.preferredCategories())
                    .budgetLimit(option.budgetLimit())
                    .parkingNeeded(option.parkingNeeded())
                    .excludedFoods(option.excludedFoods())
                    .atmosphere(option.atmosphere())
                    .build());
        } else {
            existing.update(option.walkMinutes(), option.preferredCategories(), option.budgetLimit(),
                    option.parkingNeeded(), option.excludedFoods(), option.atmosphere());
        }
    }
}
