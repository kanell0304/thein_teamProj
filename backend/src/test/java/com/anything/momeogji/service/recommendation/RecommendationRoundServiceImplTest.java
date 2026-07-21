package com.anything.momeogji.service.recommendation;

import com.anything.momeogji.dto.recommendation.PersonalOptionRequest;
import com.anything.momeogji.dto.recommendation.RecommendationRequest;
import com.anything.momeogji.dto.recommendation.RecommendationResult;
import com.anything.momeogji.dto.recommendation.RestaurantRecommendation;
import com.anything.momeogji.dto.recommendation.RoundCreateRequest;
import com.anything.momeogji.dto.recommendation.RoundResponse;
import com.anything.momeogji.entity.ChatRoom;
import com.anything.momeogji.entity.Member;
import com.anything.momeogji.entity.recommendation.Meetup;
import com.anything.momeogji.entity.recommendation.MeetupParticipant;
import com.anything.momeogji.entity.recommendation.MeetupStatus;
import com.anything.momeogji.entity.recommendation.ParticipantPreference;
import com.anything.momeogji.entity.recommendation.RecommendationRound;
import com.anything.momeogji.entity.recommendation.Restaurant;
import com.anything.momeogji.entity.recommendation.RoundCandidate;
import com.anything.momeogji.entity.recommendation.SubmissionStatus;
import com.anything.momeogji.exception.recommendation.AiRecommendationException;
import com.anything.momeogji.repository.ChatRoomMemberRepository;
import com.anything.momeogji.repository.MeetupParticipantRepository;
import com.anything.momeogji.repository.MeetupRepository;
import com.anything.momeogji.repository.MemberRepository;
import com.anything.momeogji.repository.ParticipantPreferenceRepository;
import com.anything.momeogji.repository.RecommendationRoundRepository;
import com.anything.momeogji.repository.RestaurantRepository;
import com.anything.momeogji.repository.RoundCandidateRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class RecommendationRoundServiceImplTest {

    @Mock
    private MeetupRepository meetupRepository;
    @Mock
    private ChatRoomMemberRepository chatRoomMemberRepository;
    @Mock
    private MeetupParticipantRepository meetupParticipantRepository;
    @Mock
    private MemberRepository memberRepository;
    @Mock
    private ParticipantPreferenceRepository participantPreferenceRepository;
    @Mock
    private RecommendationRoundRepository recommendationRoundRepository;
    @Mock
    private RoundCandidateRepository roundCandidateRepository;
    @Mock
    private RestaurantRepository restaurantRepository;
    @Mock
    private RestaurantRecommendationService restaurantRecommendationService;
    @Mock
    private RecommendationEventPublisher eventPublisher;
    @Mock
    private RoundResponseAssembler roundResponseAssembler;

    @InjectMocks
    private RecommendationRoundServiceImpl service;

    private Meetup meetup;

    @BeforeEach
    void setUp() {
        ChatRoom chatRoom = ChatRoom.builder().id(10L).name("테스트방").build();
        Member host = Member.builder().id(1L).kakaoId("k1").nickname("호스트").build();
        meetup = Meetup.builder()
                .id(100L)
                .chatRoom(chatRoom)
                .hostUser(host)
                .status(MeetupStatus.RECOMMENDING)
                .destinationName("강남역")
                .destinationLatitude(BigDecimal.valueOf(37.498))
                .destinationLongitude(BigDecimal.valueOf(127.027))
                .meetingTime(LocalDateTime.of(2026, 7, 20, 12, 0))
                .purpose("식사")
                .build();

        // persistPreferences()가 참여자 1L에 대해 거치는 경로 - 실제로 쓰는 테스트에서만 매칭되고, 안 쓰는 테스트는 lenient라 무시됨
        MeetupParticipant participant = MeetupParticipant.builder()
                .id(50L).meetup(meetup).user(host)
                .submissionStatus(SubmissionStatus.SUBMITTED).confirmedForAi(true)
                .build();
        lenient().when(memberRepository.findById(1L)).thenReturn(Optional.of(host));
        lenient().when(meetupParticipantRepository.findByMeetupIdAndUserId(100L, 1L)).thenReturn(Optional.of(participant));
        lenient().when(participantPreferenceRepository.findByMeetupParticipantId(50L)).thenReturn(Optional.empty());
    }

    @Test
    void 이전_회차_후보를_제외목록으로_파생해서_추천을_요청한다() {
        given(meetupRepository.findById(100L)).willReturn(Optional.of(meetup));
        given(chatRoomMemberRepository.existsByChatRoomIdAndUserId(10L, 1L)).willReturn(true);

        Restaurant prevRestaurant = Restaurant.builder().id(5L).kakaoPlaceId("prev-1").name("이전맛집").build();
        RecommendationRound prevRound = RecommendationRound.builder().id(1L).meetup(meetup).roundNo(1).build();
        RoundCandidate prevCandidate = RoundCandidate.builder().id(9L).round(prevRound).restaurant(prevRestaurant).rankNo(1).build();
        given(roundCandidateRepository.findByRound_Meetup_Id(100L)).willReturn(List.of(prevCandidate));
        given(recommendationRoundRepository.countByMeetupId(100L)).willReturn(1L);

        RecommendationResult aiResult = new RecommendationResult(2, List.of(
                new RestaurantRecommendation("new-1", 1, "새맛집", "한식", "도로명", "지번", 37.5, 127.0, "이유", "img")
        ));
        given(restaurantRecommendationService.recommend(any())).willReturn(aiResult);

        Restaurant newRestaurant = Restaurant.builder().id(6L).kakaoPlaceId("new-1").name("새맛집").build();
        given(restaurantRepository.findByKakaoPlaceId("new-1")).willReturn(Optional.empty());
        given(restaurantRepository.save(any())).willReturn(newRestaurant);

        RecommendationRound savedRound = RecommendationRound.builder().id(2L).meetup(meetup).roundNo(2).build();
        given(recommendationRoundRepository.save(any())).willReturn(savedRound);

        RoundResponse expectedResponse = new RoundResponse(100L, 2L, 2, 2, List.of());
        given(roundResponseAssembler.assemble(savedRound)).willReturn(expectedResponse);

        RoundCreateRequest request = new RoundCreateRequest(List.of(personalOption(1L)), null);

        RoundResponse response = service.createRound(100L, request, 1L);

        assertThat(response).isEqualTo(expectedResponse);

        ArgumentCaptor<RecommendationRequest> captor = ArgumentCaptor.forClass(RecommendationRequest.class);
        verify(restaurantRecommendationService).recommend(captor.capture());
        assertThat(captor.getValue().excludedRestaurantIds()).containsExactly("prev-1");

        verify(eventPublisher).recommendationStarted(10L);
        verify(eventPublisher).recommendationCompleted(10L, expectedResponse);
        verify(roundCandidateRepository).save(any());
        assertThat(meetup.getStatus()).isEqualTo(MeetupStatus.VOTING);
    }

    @Test
    void 채팅방_참여자가_아니면_예외() {
        given(meetupRepository.findById(100L)).willReturn(Optional.of(meetup));
        given(chatRoomMemberRepository.existsByChatRoomIdAndUserId(10L, 1L)).willReturn(false);

        RoundCreateRequest request = new RoundCreateRequest(List.of(personalOption(1L)), null);

        assertThatThrownBy(() -> service.createRound(100L, request, 1L))
                .isInstanceOf(IllegalArgumentException.class);

        verifyNoInteractions(restaurantRecommendationService);
    }

    @Test
    void AI_호출이_실패하면_FAILED를_브로드캐스트하고_재throw한다() {
        given(meetupRepository.findById(100L)).willReturn(Optional.of(meetup));
        given(chatRoomMemberRepository.existsByChatRoomIdAndUserId(10L, 1L)).willReturn(true);
        given(roundCandidateRepository.findByRound_Meetup_Id(100L)).willReturn(List.of());
        given(restaurantRecommendationService.recommend(any())).willThrow(new AiRecommendationException("실패"));

        RoundCreateRequest request = new RoundCreateRequest(List.of(personalOption(1L)), null);

        assertThatThrownBy(() -> service.createRound(100L, request, 1L))
                .isInstanceOf(AiRecommendationException.class);

        verify(eventPublisher).recommendationStarted(10L);
        verify(eventPublisher).recommendationFailed(eq(10L), any());
        verify(recommendationRoundRepository, never()).save(any());
    }

    @Test
    void 존재하지_않는_참여자ID면_예외() {
        given(meetupRepository.findById(100L)).willReturn(Optional.of(meetup));
        given(chatRoomMemberRepository.existsByChatRoomIdAndUserId(10L, 1L)).willReturn(true);
        given(memberRepository.findById(999L)).willReturn(Optional.empty());

        RoundCreateRequest request = new RoundCreateRequest(List.of(personalOption(999L)), null);

        assertThatThrownBy(() -> service.createRound(100L, request, 1L))
                .isInstanceOf(IllegalArgumentException.class);

        verifyNoInteractions(restaurantRecommendationService);
    }

    @Test
    void 이미_선호를_제출한_참여자가_다시_제출하면_기존값을_갱신한다() {
        given(meetupRepository.findById(100L)).willReturn(Optional.of(meetup));
        given(chatRoomMemberRepository.existsByChatRoomIdAndUserId(10L, 1L)).willReturn(true);
        given(roundCandidateRepository.findByRound_Meetup_Id(100L)).willReturn(List.of());

        ParticipantPreference existing = ParticipantPreference.builder()
                .id(70L)
                .meetupParticipant(MeetupParticipant.builder().id(50L).build())
                .walkMinutes(5)
                .preferredCategories(List.of("일식"))
                .parkingNeeded(false)
                .excludedFoods(List.of())
                .build();
        given(participantPreferenceRepository.findByMeetupParticipantId(50L)).willReturn(Optional.of(existing));
        given(restaurantRecommendationService.recommend(any())).willThrow(new AiRecommendationException("이후 흐름은 이 테스트 관심사가 아님"));

        RoundCreateRequest request = new RoundCreateRequest(List.of(personalOption(1L)), null);

        assertThatThrownBy(() -> service.createRound(100L, request, 1L)).isInstanceOf(AiRecommendationException.class);

        assertThat(existing.getWalkMinutes()).isEqualTo(10);
        assertThat(existing.getPreferredCategories()).containsExactly("한식");
        verify(participantPreferenceRepository, never()).save(any());
    }

    private PersonalOptionRequest personalOption(Long participantId) {
        return new PersonalOptionRequest(participantId, 10, List.of("한식"), 15000, false, List.of(), null);
    }
}
