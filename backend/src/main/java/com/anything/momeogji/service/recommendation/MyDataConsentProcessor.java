package com.anything.momeogji.service.recommendation;

import com.anything.momeogji.entity.recommendation.ConsentStatus;
import com.anything.momeogji.entity.recommendation.MeetupParticipant;
import com.anything.momeogji.entity.recommendation.MydataConsent;
import com.anything.momeogji.entity.recommendation.ProcessingStatus;
import com.anything.momeogji.mydata.MyDataService;
import com.anything.momeogji.mydata.transform.model.KakaoPlaceMatchData;
import com.anything.momeogji.mydata.transform.model.MerchantUsageData;
import com.anything.momeogji.mydata.transform.model.TimeBand;
import com.anything.momeogji.mydata.transform.model.TransformedUserMyData;
import com.anything.momeogji.repository.MydataConsentRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 참여자의 마이데이터 활용 동의를 기록하고, 동의한 경우 결제 이력에서 음식점(FD6)/카페(CE7) 방문 횟수를 뽑아 저장한다.
 * 더미 마이데이터가 없는 참가자가 대부분이라 수집이 실패하는 게 일반적인 경우이므로, 실패해도 개인 선호 제출 자체를
 * 막지 않고 처리 상태만 FAILED로 기록한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MyDataConsentProcessor {

    private final MydataConsentRepository mydataConsentRepository;
    private final MyDataService myDataService;
    private final ObjectMapper objectMapper;

    public void process(MeetupParticipant participant, boolean consentGiven, LocalDateTime meetingTime) {
        ConsentStatus consentStatus = consentGiven ? ConsentStatus.AGREED : ConsentStatus.DECLINED;
        ProcessingStatus processingStatus = ProcessingStatus.COMPLETED;
        String processedResult = null;

        if (consentGiven) {
            try {
                TimeBand timeBand = TimeBand.fromTime(meetingTime.toLocalTime());
                TransformedUserMyData myData = myDataService.collectTransformed(participant.getUser().getId(), timeBand);
                processedResult = objectMapper.writeValueAsString(summarize(myData));
            } catch (RuntimeException | JsonProcessingException e) {
                log.info("마이데이터 처리를 건너뜁니다(participantId={}): {}", participant.getId(), e.getMessage());
                processingStatus = ProcessingStatus.FAILED;
            }
        }

        MydataConsent existing = mydataConsentRepository.findByMeetupParticipantId(participant.getId()).orElse(null);
        if (existing == null) {
            mydataConsentRepository.save(MydataConsent.builder()
                    .meetupParticipant(participant)
                    .consentStatus(consentStatus)
                    .processingStatus(processingStatus)
                    .processedResult(processedResult)
                    .build());
        } else {
            existing.update(consentStatus, processingStatus, processedResult);
        }
    }

    private CategoryUsageSummary summarize(TransformedUserMyData myData) {
        long fd6Count = 0;
        long ce7Count = 0;
        for (MerchantUsageData usage : myData.merchantUsages()) {
            String categoryCode = usage.kakaoPlaceMatch().categoryCode();
            if (KakaoPlaceMatchData.RESTAURANT_CATEGORY_CODE.equals(categoryCode)) {
                fd6Count++;
            } else if (KakaoPlaceMatchData.CAFE_CATEGORY_CODE.equals(categoryCode)) {
                ce7Count++;
            }
        }
        return new CategoryUsageSummary(fd6Count, ce7Count);
    }
}
