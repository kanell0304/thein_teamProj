package com.anything.momeogji.service.recommendation;

import com.anything.momeogji.dto.recommendation.ParticipantSummaryResponse;
import com.anything.momeogji.dto.recommendation.MeetupProgressEvent;
import com.anything.momeogji.dto.recommendation.PersonalOptionRequest;
import com.anything.momeogji.dto.recommendation.PreferenceSubmitRequest;
import com.anything.momeogji.dto.recommendation.PreferenceSubmitResponse;
import com.anything.momeogji.dto.recommendation.RoundResponse;
import com.anything.momeogji.entity.recommendation.Meetup;
import com.anything.momeogji.entity.recommendation.MeetupParticipant;
import com.anything.momeogji.entity.recommendation.ParticipantPreference;
import com.anything.momeogji.entity.recommendation.SubmissionStatus;
import com.anything.momeogji.mydata.MyDataService;
import com.anything.momeogji.mydata.processing.model.MyDataRestaurantData;
import com.anything.momeogji.repository.MeetupParticipantRepository;
import com.anything.momeogji.repository.MeetupRepository;
import com.anything.momeogji.repository.ParticipantPreferenceRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class MeetupPreferenceServiceImpl implements MeetupPreferenceService {

    private static final Logger log = LoggerFactory.getLogger(MeetupPreferenceServiceImpl.class);

    private final MeetupRepository meetupRepository;
    private final MeetupParticipantRepository meetupParticipantRepository;
    private final ParticipantPreferenceRepository participantPreferenceRepository;
    private final MyDataService myDataService;
    private final MeetupMyDataResultStore meetupMyDataResultStore;
    private final RecommendationRoundService recommendationRoundService;
    private final MeetupService meetupService;
    private final RecommendationEventPublisher eventPublisher;

    @Override
    @Transactional
    public PreferenceSubmitResponse submitPreference(Long meetupId, Long callerId, PreferenceSubmitRequest request) {
        Meetup meetup = findMeetup(meetupId);
        MeetupParticipant participant = meetupParticipantRepository.findByMeetupIdAndUserId(meetupId, callerId)
                .orElseThrow(() -> new IllegalArgumentException("초대받은 참여자만 개인 선호를 제출할 수 있습니다."));

        if (participantPreferenceRepository.existsByMeetupParticipantId(participant.getId())) {
            throw new IllegalStateException("개인 옵션은 한 번만 제출할 수 있습니다.");
        }

        participantPreferenceRepository.save(ParticipantPreference.builder()
                .meetupParticipant(participant)
                .walkMinutes(request.walkMinutes())
                .preferredCategories(request.preferredCategories())
                .budgetLimit(request.budgetLimit())
                .parkingNeeded(request.parkingNeeded())
                .excludedFoods(request.excludedFoods())
                .atmosphere(request.atmosphere())
                .myDataConsent(request.myDataConsent())
                .build());
        participant.markSubmitted();

        if (request.myDataConsent()) {
            try {
                // 모임 시각과 목적을 함께 전달해 시간대·음식점/카페 범위에 맞는 MyData만 처리한다.
                List<MyDataRestaurantData> myDataRestaurants = myDataService.process(
                        callerId,
                        meetup.getMeetingTime().toLocalTime(),
                        meetup.getPurpose()
                );

                // 추천 시작 시 전체 참여자의 결과를 합칠 수 있도록 모임·사용자 키로 임시 보관한다.
                meetupMyDataResultStore.save(
                        meetupId,
                        callerId,
                        myDataRestaurants
                );
            } catch (RuntimeException exception) {
                // 선택 기능인 MyData 실패가 개인 옵션 저장과 전체 추천 진행을 막지 않게 격리한다.
                log.warn(
                        "개인 옵션 제출 중 MyData 처리를 생략했습니다. meetupId={}, userId={}",
                        meetupId,
                        callerId,
                        exception
                );
            }
        }

        List<ParticipantSummaryResponse> participants = meetupService.listParticipants(meetupId);
        eventPublisher.preferenceProgress(meetup.getChatRoom().getId(), new MeetupProgressEvent(meetupId, participants));

        int totalCount = participants.size();
        long pendingCount = meetupParticipantRepository.countByMeetupIdAndSubmissionStatus(meetupId, SubmissionStatus.PENDING);
        int submittedCount = totalCount - (int) pendingCount;

        if (pendingCount == 0) {
            RoundResponse round = triggerRecommendation(meetup);
            return new PreferenceSubmitResponse(meetupId, submittedCount, totalCount, true, round);
        }

        return new PreferenceSubmitResponse(meetupId, submittedCount, totalCount, false, null);
    }

    @Override
    @Transactional
    public RoundResponse forceStartRecommendation(Long meetupId, Long callerId) {
        Meetup meetup = findMeetup(meetupId);
        if (!meetup.getHostUser().getId().equals(callerId)) {
            throw new IllegalArgumentException("호스트만 강제로 진행할 수 있습니다.");
        }

        List<ParticipantPreference> preferences = participantPreferenceRepository.findByMeetupParticipant_Meetup_Id(meetupId);
        if (preferences.isEmpty()) {
            throw new IllegalArgumentException("아직 제출된 개인 선호가 없어 진행할 수 없습니다.");
        }

        return triggerRecommendation(meetup, preferences);
    }

    private RoundResponse triggerRecommendation(Meetup meetup) {
        return triggerRecommendation(meetup, participantPreferenceRepository.findByMeetupParticipant_Meetup_Id(meetup.getId()));
    }

    private RoundResponse triggerRecommendation(Meetup meetup, List<ParticipantPreference> preferences) {
        List<PersonalOptionRequest> options = preferences.stream()
                .map(preference -> new PersonalOptionRequest(
                        preference.getMeetupParticipant().getUser().getId(),
                        preference.getWalkMinutes(),
                        preference.getPreferredCategories(),
                        preference.getBudgetLimit(),
                        preference.isParkingNeeded(),
                        preference.getExcludedFoods(),
                        preference.getAtmosphere()))
                .toList();
        return recommendationRoundService.triggerAutoRecommendation(meetup, options);
    }

    private Meetup findMeetup(Long meetupId) {
        return meetupRepository.findById(meetupId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 모임입니다: " + meetupId));
    }
}
