package com.anything.momeogji.service.recommendation;

import com.anything.momeogji.dto.recommendation.ParticipantSummaryResponse;
import com.anything.momeogji.dto.recommendation.PreferenceSubmitRequest;
import com.anything.momeogji.dto.recommendation.PreferenceSubmitResponse;
import com.anything.momeogji.entity.ChatRoom;
import com.anything.momeogji.entity.recommendation.Meetup;
import com.anything.momeogji.entity.recommendation.MeetupParticipant;
import com.anything.momeogji.entity.recommendation.ParticipantPreference;
import com.anything.momeogji.entity.recommendation.SubmissionStatus;
import com.anything.momeogji.mydata.MyDataService;
import com.anything.momeogji.mydata.processing.model.MyDataRestaurantData;
import com.anything.momeogji.repository.MeetupParticipantRepository;
import com.anything.momeogji.repository.MeetupRepository;
import com.anything.momeogji.repository.ParticipantPreferenceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * {@link MeetupPreferenceServiceImpl}가 본인 선호 저장과 선택적 MyData 호출을 분리된 실패 정책으로 처리하는지 검증한다.
 */
@ExtendWith(MockitoExtension.class)
class MeetupPreferenceServiceImplTest {

    @Mock
    private MeetupRepository meetupRepository;

    @Mock
    private MeetupParticipantRepository meetupParticipantRepository;

    @Mock
    private ParticipantPreferenceRepository participantPreferenceRepository;

    @Mock
    private MyDataService myDataService;

    @Mock
    private MeetupMyDataResultStore meetupMyDataResultStore;

    @Mock
    private RecommendationRoundService recommendationRoundService;

    @Mock
    private MeetupService meetupService;

    @Mock
    private RecommendationEventPublisher eventPublisher;

    private MeetupPreferenceServiceImpl meetupPreferenceService;

    /**
     * 테스트마다 개인 선호 저장 서비스에 모든 외부 경계를 Mock으로 주입한다.
     */
    @BeforeEach
    void setUp() {
        meetupPreferenceService = new MeetupPreferenceServiceImpl(
                meetupRepository,
                meetupParticipantRepository,
                participantPreferenceRepository,
                myDataService,
                meetupMyDataResultStore,
                recommendationRoundService,
                meetupService,
                eventPublisher
        );
    }

    /**
     * MyData 처리 실패가 개인 옵션 저장과 제출 진행 이벤트를 중단하지 않는지 확인한다.
     */
    @Test
    void 마이데이터_실패에도_개인_옵션_제출은_완료된다() {
        Long meetupId = 10L;
        Long userId = 20L;
        LocalDateTime meetingTime = LocalDateTime.of(2026, 7, 24, 12, 30);
        ChatRoom chatRoom = ChatRoom.builder()
                .id(30L)
                .name("점심 모임")
                .build();
        Meetup meetup = Meetup.builder()
                .id(meetupId)
                .chatRoom(chatRoom)
                .meetingTime(meetingTime)
                .purpose("식사")
                .build();
        MeetupParticipant participant = MeetupParticipant.builder()
                .id(40L)
                .meetup(meetup)
                .submissionStatus(SubmissionStatus.PENDING)
                .build();
        PreferenceSubmitRequest request = new PreferenceSubmitRequest(
                10,
                List.of("한식"),
                20_000,
                false,
                List.of("갑각류"),
                "조용한 곳",
                true
        );
        List<ParticipantSummaryResponse> participants = List.of(
                new ParticipantSummaryResponse(40L, userId, "참가자", "SUBMITTED", false)
        );

        given(meetupRepository.findById(meetupId)).willReturn(Optional.of(meetup));
        given(meetupParticipantRepository.findByMeetupIdAndUserId(meetupId, userId))
                .willReturn(Optional.of(participant));
        given(participantPreferenceRepository.existsByMeetupParticipantId(40L))
                .willReturn(false);
        given(meetupService.listParticipants(meetupId)).willReturn(participants);
        given(meetupParticipantRepository.countByMeetupIdAndSubmissionStatus(
                meetupId,
                SubmissionStatus.PENDING
        )).willReturn(1L);
        doThrow(new IllegalStateException("카카오 검색 실패"))
                .when(myDataService)
                .process(userId, meetingTime.toLocalTime(), "식사");

        PreferenceSubmitResponse result = meetupPreferenceService.submitPreference(
                meetupId,
                userId,
                request
        );

        ArgumentCaptor<ParticipantPreference> preferenceCaptor =
                ArgumentCaptor.forClass(ParticipantPreference.class);
        verify(participantPreferenceRepository).save(preferenceCaptor.capture());
        assertThat(preferenceCaptor.getValue().isMyDataConsent()).isTrue();
        assertThat(participant.getSubmissionStatus()).isEqualTo(SubmissionStatus.SUBMITTED);
        assertThat(result.meetupId()).isEqualTo(meetupId);
        assertThat(result.recommendationTriggered()).isFalse();
        verify(myDataService).process(userId, meetingTime.toLocalTime(), "식사");
        verify(meetupMyDataResultStore, never())
                .save(
                        org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.anyList()
                );
        verify(eventPublisher).preferenceProgress(
                org.mockito.ArgumentMatchers.eq(chatRoom.getId()),
                org.mockito.ArgumentMatchers.any()
        );
        verify(recommendationRoundService, never())
                .triggerAutoRecommendation(
                        org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.anyList()
                );
    }

    /**
     * MyData 처리가 성공하면 추천 시작 여부를 판단하기 전에 모임·사용자 키로 결과를 저장하는지 확인한다.
     */
    @Test
    void 마이데이터_성공_결과를_모임과_사용자별로_저장한다() {
        Long meetupId = 10L;
        Long userId = 20L;
        LocalDateTime meetingTime = LocalDateTime.of(2026, 7, 24, 12, 30);
        ChatRoom chatRoom = ChatRoom.builder()
                .id(30L)
                .name("점심 모임")
                .build();
        Meetup meetup = Meetup.builder()
                .id(meetupId)
                .chatRoom(chatRoom)
                .meetingTime(meetingTime)
                .purpose("식사")
                .build();
        MeetupParticipant participant = MeetupParticipant.builder()
                .id(40L)
                .meetup(meetup)
                .submissionStatus(SubmissionStatus.PENDING)
                .build();
        PreferenceSubmitRequest request = new PreferenceSubmitRequest(
                10,
                List.of("한식"),
                20_000,
                false,
                List.of(),
                null,
                true
        );
        List<MyDataRestaurantData> restaurants = List.of(
                new MyDataRestaurantData("영인성", "중식 > 중화요리")
        );

        given(meetupRepository.findById(meetupId)).willReturn(Optional.of(meetup));
        given(meetupParticipantRepository.findByMeetupIdAndUserId(meetupId, userId))
                .willReturn(Optional.of(participant));
        given(participantPreferenceRepository.existsByMeetupParticipantId(40L))
                .willReturn(false);
        given(myDataService.process(userId, meetingTime.toLocalTime(), "식사"))
                .willReturn(restaurants);
        given(meetupService.listParticipants(meetupId)).willReturn(List.of(
                new ParticipantSummaryResponse(40L, userId, "참가자", "SUBMITTED", false)
        ));
        given(meetupParticipantRepository.countByMeetupIdAndSubmissionStatus(
                meetupId,
                SubmissionStatus.PENDING
        )).willReturn(1L);

        meetupPreferenceService.submitPreference(meetupId, userId, request);

        verify(meetupMyDataResultStore).save(
                meetupId,
                userId,
                restaurants
        );
    }
}
