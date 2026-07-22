package com.anything.momeogji.service.recommendation;

import com.anything.momeogji.dto.recommendation.PersonalOptionRequest;
import com.anything.momeogji.entity.Member;
import com.anything.momeogji.entity.recommendation.ConsentStatus;
import com.anything.momeogji.entity.recommendation.MeetupParticipant;
import com.anything.momeogji.entity.recommendation.MydataConsent;
import com.anything.momeogji.entity.recommendation.ParticipantPreference;
import com.anything.momeogji.entity.recommendation.ProcessingStatus;
import com.anything.momeogji.repository.MydataConsentRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;

/** MyData 기반 음식점/카페 방문 신호가 preferredCategories에 반영되는 조건을 검증한다. */
@ExtendWith(MockitoExtension.class)
class MyDataCategoryEnricherTest {

    private static final Long MEETUP_PARTICIPANT_ID = 50L;

    @Mock
    private MydataConsentRepository mydataConsentRepository;

    private MyDataCategoryEnricher enricher;
    private List<ParticipantPreference> preferences;
    private PersonalOptionRequest baseOption;

    @BeforeEach
    void setUp() {
        enricher = new MyDataCategoryEnricher(mydataConsentRepository, new ObjectMapper());

        Member member = Member.builder().id(1L).kakaoId("k1").nickname("호스트").build();
        MeetupParticipant participant = MeetupParticipant.builder().id(MEETUP_PARTICIPANT_ID).user(member).build();
        ParticipantPreference preference = ParticipantPreference.builder().meetupParticipant(participant).build();
        preferences = List.of(preference);

        baseOption = new PersonalOptionRequest(1L, 10, List.of("한식"), 20000, false, List.of(), null);
    }

    private MydataConsent consentWith(ConsentStatus consentStatus, ProcessingStatus processingStatus, String processedResult) {
        return MydataConsent.builder()
                .consentStatus(consentStatus)
                .processingStatus(processingStatus)
                .processedResult(processedResult)
                .build();
    }

    @Test
    void 카페_방문이_많으면_카페디저트를_추가한다() {
        given(mydataConsentRepository.findByMeetupParticipantId(MEETUP_PARTICIPANT_ID))
                .willReturn(Optional.of(consentWith(ConsentStatus.AGREED, ProcessingStatus.COMPLETED,
                        "{\"fd6Count\":1,\"ce7Count\":10}")));

        List<PersonalOptionRequest> enriched = enricher.enrich(preferences, List.of(baseOption));

        assertThat(enriched.get(0).preferredCategories()).containsExactly("한식", "카페/디저트");
    }

    @Test
    void 음식점_방문이_더_많으면_추가하지_않는다() {
        given(mydataConsentRepository.findByMeetupParticipantId(MEETUP_PARTICIPANT_ID))
                .willReturn(Optional.of(consentWith(ConsentStatus.AGREED, ProcessingStatus.COMPLETED,
                        "{\"fd6Count\":10,\"ce7Count\":4}")));

        List<PersonalOptionRequest> enriched = enricher.enrich(preferences, List.of(baseOption));

        assertThat(enriched.get(0).preferredCategories()).containsExactly("한식");
    }

    @Test
    void 동의하지_않았으면_추가하지_않는다() {
        given(mydataConsentRepository.findByMeetupParticipantId(MEETUP_PARTICIPANT_ID))
                .willReturn(Optional.of(consentWith(ConsentStatus.DECLINED, ProcessingStatus.COMPLETED, null)));

        List<PersonalOptionRequest> enriched = enricher.enrich(preferences, List.of(baseOption));

        assertThat(enriched.get(0).preferredCategories()).containsExactly("한식");
    }

    @Test
    void 처리가_실패했으면_추가하지_않는다() {
        given(mydataConsentRepository.findByMeetupParticipantId(MEETUP_PARTICIPANT_ID))
                .willReturn(Optional.of(consentWith(ConsentStatus.AGREED, ProcessingStatus.FAILED, null)));

        List<PersonalOptionRequest> enriched = enricher.enrich(preferences, List.of(baseOption));

        assertThat(enriched.get(0).preferredCategories()).containsExactly("한식");
    }

    @Test
    void 동의_기록이_없으면_추가하지_않는다() {
        given(mydataConsentRepository.findByMeetupParticipantId(MEETUP_PARTICIPANT_ID))
                .willReturn(Optional.empty());

        List<PersonalOptionRequest> enriched = enricher.enrich(preferences, List.of(baseOption));

        assertThat(enriched.get(0).preferredCategories()).containsExactly("한식");
    }

    @Test
    void 이미_카페디저트가_있으면_중복_추가하지_않는다() {
        // 이미 카페/디저트가 있으면 MyData 조회 자체를 생략하므로 스텁을 걸지 않는다.
        PersonalOptionRequest optionWithCafe = new PersonalOptionRequest(1L, 10, List.of("카페/디저트"), 20000, false, List.of(), null);

        List<PersonalOptionRequest> enriched = enricher.enrich(preferences, List.of(optionWithCafe));

        assertThat(enriched.get(0).preferredCategories()).containsExactly("카페/디저트");
        verifyNoInteractions(mydataConsentRepository);
    }
}
