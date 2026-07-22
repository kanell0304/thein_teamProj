package com.anything.momeogji.service.recommendation;

import com.anything.momeogji.dto.recommendation.RoundResponse;
import com.anything.momeogji.dto.recommendation.VoteSelectionRequest;
import com.anything.momeogji.entity.ChatRoom;
import com.anything.momeogji.entity.Member;
import com.anything.momeogji.entity.recommendation.Meetup;
import com.anything.momeogji.entity.recommendation.MeetupParticipant;
import com.anything.momeogji.entity.recommendation.MeetupStatus;
import com.anything.momeogji.entity.recommendation.RecommendationRound;
import com.anything.momeogji.entity.recommendation.Restaurant;
import com.anything.momeogji.entity.recommendation.RoundCandidate;
import com.anything.momeogji.entity.recommendation.SubmissionStatus;
import com.anything.momeogji.repository.ChatRoomMemberRepository;
import com.anything.momeogji.repository.MeetupParticipantRepository;
import com.anything.momeogji.repository.MeetupRepository;
import com.anything.momeogji.repository.MemberRepository;
import com.anything.momeogji.repository.ParticipantPreferenceRepository;
import com.anything.momeogji.repository.RecommendationRoundRepository;
import com.anything.momeogji.repository.RoundCandidateRepository;
import com.anything.momeogji.repository.VoteRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/** 투표 마감시간(voteDeadlineAt) 적용 전/후 동작을 검증한다. */
@ExtendWith(MockitoExtension.class)
class MeetupVoteServiceImplTest {

    @Mock
    private MeetupRepository meetupRepository;
    @Mock
    private RecommendationRoundRepository recommendationRoundRepository;
    @Mock
    private RoundCandidateRepository roundCandidateRepository;
    @Mock
    private MeetupParticipantRepository meetupParticipantRepository;
    @Mock
    private MemberRepository memberRepository;
    @Mock
    private ChatRoomMemberRepository chatRoomMemberRepository;
    @Mock
    private VoteRepository voteRepository;
    @Mock
    private ParticipantPreferenceRepository participantPreferenceRepository;
    @Mock
    private RecommendationEventPublisher eventPublisher;
    @Mock
    private RoundResponseAssembler roundResponseAssembler;
    @Mock
    private MeetupFinalizeService meetupFinalizeService;
    @Mock
    private RecommendationRoundService recommendationRoundService;

    @InjectMocks
    private MeetupVoteServiceImpl service;

    private Meetup meetup;
    private RecommendationRound round;
    private RoundCandidate candidate;

    @BeforeEach
    void setUp() {
        ChatRoom chatRoom = ChatRoom.builder().id(10L).name("테스트방").build();
        Member host = Member.builder().id(1L).kakaoId("k1").nickname("호스트").build();
        meetup = Meetup.builder()
                .id(100L).chatRoom(chatRoom).hostUser(host).status(MeetupStatus.VOTING)
                .destinationName("강남역")
                .destinationLatitude(BigDecimal.valueOf(37.498))
                .destinationLongitude(BigDecimal.valueOf(127.027))
                .meetingTime(LocalDateTime.of(2026, 7, 20, 12, 0))
                .purpose("식사")
                .build();
        round = RecommendationRound.builder().id(2L).meetup(meetup).roundNo(1).build();
        Restaurant restaurant = Restaurant.builder().id(1L).kakaoPlaceId("p1").name("맛집1").build();
        candidate = RoundCandidate.builder().id(11L).round(round).restaurant(restaurant).rankNo(1).build();

        lenient().when(recommendationRoundRepository.findById(2L)).thenReturn(Optional.of(round));
        lenient().when(roundCandidateRepository.findById(11L)).thenReturn(Optional.of(candidate));
        lenient().when(chatRoomMemberRepository.existsByChatRoomIdAndUserId(10L, 1L)).thenReturn(true);
        lenient().when(roundResponseAssembler.assemble(round)).thenReturn(new RoundResponse(
                100L, 2L, 1, 1, 0, "VOTING", null, java.util.List.of()));
    }

    @Test
    void 마감시간_전이면_투표_가능() {
        meetup = withDeadline(LocalDateTime.now().plusMinutes(10));
        given(voteRepository.findByRoundCandidateIdAndMeetupParticipantId(11L, 50L)).willReturn(Optional.empty());
        given(meetupParticipantRepository.findByMeetupIdAndUserId(100L, 1L))
                .willReturn(Optional.of(MeetupParticipant.builder().id(50L).meetup(meetup).build()));

        RoundResponse response = service.castVote(100L, 2L, 11L, 1L);

        assertThat(response).isNotNull();
    }

    @Test
    void 마감시간이_지나면_투표_불가() {
        meetup = withDeadline(LocalDateTime.now().minusMinutes(1));

        assertThatThrownBy(() -> service.castVote(100L, 2L, 11L, 1L))
                .isInstanceOf(IllegalArgumentException.class);

        verifyNoInteractions(voteRepository);
    }

    @Test
    void 마감시간이_지나면_투표_취소도_불가() {
        meetup = withDeadline(LocalDateTime.now().minusMinutes(1));

        assertThatThrownBy(() -> service.retractVote(100L, 2L, 11L, 1L))
                .isInstanceOf(IllegalArgumentException.class);

        verifyNoInteractions(voteRepository);
    }

    @Test
    void 마감시간이_없으면_계속_투표_가능() {
        given(voteRepository.findByRoundCandidateIdAndMeetupParticipantId(11L, 50L)).willReturn(Optional.empty());
        given(meetupParticipantRepository.findByMeetupIdAndUserId(100L, 1L))
                .willReturn(Optional.of(MeetupParticipant.builder().id(50L).meetup(meetup).build()));

        RoundResponse response = service.castVote(100L, 2L, 11L, 1L);

        assertThat(response).isNotNull();
    }

    @Test
    void 제출한_전원이_투표하면_자동으로_확정한다() {
        given(voteRepository.findByRoundCandidateIdAndMeetupParticipantId(11L, 50L)).willReturn(Optional.empty());
        given(meetupParticipantRepository.findByMeetupIdAndUserId(100L, 1L))
                .willReturn(Optional.of(MeetupParticipant.builder().id(50L).meetup(meetup).build()));
        given(meetupParticipantRepository.countByMeetupIdAndSubmissionStatus(100L, SubmissionStatus.SUBMITTED)).willReturn(1L);
        given(voteRepository.countDistinctVotersByRoundId(2L)).willReturn(1L);

        service.castVote(100L, 2L, 11L, 1L);

        verify(meetupFinalizeService).finalizeInternal(meetup);
    }

    @Test
    void 일부만_투표하면_자동으로_확정하지_않는다() {
        given(voteRepository.findByRoundCandidateIdAndMeetupParticipantId(11L, 50L)).willReturn(Optional.empty());
        given(meetupParticipantRepository.findByMeetupIdAndUserId(100L, 1L))
                .willReturn(Optional.of(MeetupParticipant.builder().id(50L).meetup(meetup).build()));
        given(meetupParticipantRepository.countByMeetupIdAndSubmissionStatus(100L, SubmissionStatus.SUBMITTED)).willReturn(2L);
        given(voteRepository.countDistinctVotersByRoundId(2L)).willReturn(1L);

        service.castVote(100L, 2L, 11L, 1L);

        verify(meetupFinalizeService, never()).finalizeInternal(any());
    }

    @Test
    void 복수_선택은_한_요청에서_모두_교체한다() {
        RoundCandidate secondCandidate = RoundCandidate.builder()
                .id(12L).round(round)
                .restaurant(Restaurant.builder().id(2L).kakaoPlaceId("p2").name("맛집2").build())
                .rankNo(2).build();
        MeetupParticipant participant = MeetupParticipant.builder().id(50L).meetup(meetup).build();
        given(roundCandidateRepository.findByRoundId(2L)).willReturn(List.of(candidate, secondCandidate));
        given(meetupParticipantRepository.findByMeetupIdAndUserId(100L, 1L)).willReturn(Optional.of(participant));
        given(voteRepository.findByRoundCandidateRoundIdAndMeetupParticipantId(2L, 50L)).willReturn(List.of());
        given(meetupParticipantRepository.countByMeetupIdAndSubmissionStatus(100L, SubmissionStatus.SUBMITTED)).willReturn(2L);
        given(voteRepository.countDistinctVotersByRoundId(2L)).willReturn(1L);

        service.replaceVotes(100L, 2L, new VoteSelectionRequest(List.of(11L, 12L)), 1L);

        verify(voteRepository, times(2)).save(any());
        verify(meetupFinalizeService, never()).finalizeInternal(any());
    }

    @Test
    void 재투표가_단독_일등이면_새_추천_회차를_생성한다() {
        RoundCandidate recommendAgain = RoundCandidate.builder()
                .id(14L).round(round)
                .restaurant(Restaurant.builder().id(4L)
                        .kakaoPlaceId(RecommendationRoundServiceImpl.RECOMMEND_AGAIN_PLACE_ID)
                        .name("재투표").build())
                .rankNo(4).build();
        MeetupParticipant participant = MeetupParticipant.builder().id(50L).meetup(meetup).build();
        RoundResponse nextRound = new RoundResponse(100L, 3L, 2, 1, 0, "VOTING", null, List.of());
        given(roundCandidateRepository.findByRoundId(2L)).willReturn(List.of(candidate, recommendAgain));
        given(meetupParticipantRepository.findByMeetupIdAndUserId(100L, 1L)).willReturn(Optional.of(participant));
        given(voteRepository.findByRoundCandidateRoundIdAndMeetupParticipantId(2L, 50L)).willReturn(List.of());
        given(meetupParticipantRepository.countByMeetupIdAndSubmissionStatus(100L, SubmissionStatus.SUBMITTED)).willReturn(1L);
        given(voteRepository.countDistinctVotersByRoundId(2L)).willReturn(1L);
        given(voteRepository.countByRoundCandidateId(14L)).willReturn(1L);
        given(voteRepository.countByRoundCandidateId(11L)).willReturn(0L);
        given(participantPreferenceRepository.findByMeetupParticipant_Meetup_Id(100L)).willReturn(List.of());
        given(recommendationRoundService.triggerAutoRecommendation(meetup, List.of())).willReturn(nextRound);

        RoundResponse response = service.replaceVotes(
                100L, 2L, new VoteSelectionRequest(List.of(14L)), 1L);

        assertThat(response).isEqualTo(nextRound);
        verify(recommendationRoundService).triggerAutoRecommendation(meetup, List.of());
        verify(meetupFinalizeService, never()).finalizeInternal(any());
    }

    @Test
    void 마감된_투표에_표가_없으면_음식점을_무작위_확정한다() {
        meetup = withDeadline(LocalDateTime.now().minusSeconds(1));
        given(meetupRepository.findByStatusAndVoteDeadlineAtLessThanEqual(
                org.mockito.ArgumentMatchers.eq(MeetupStatus.VOTING), any(LocalDateTime.class)))
                .willReturn(List.of(meetup));
        given(recommendationRoundRepository.findFirstByMeetupIdOrderByRoundNoDesc(100L))
                .willReturn(Optional.of(round));
        given(roundCandidateRepository.findByRoundId(2L)).willReturn(List.of(candidate));
        given(voteRepository.countDistinctVotersByRoundId(2L)).willReturn(0L);

        service.resolveExpiredVotes();

        verify(meetupFinalizeService).finalizeAfterDeadlineInternal(meetup);
    }

    @Test
    void 마감된_투표에_표가_있으면_현재_득표로_확정한다() {
        meetup = withDeadline(LocalDateTime.now().minusSeconds(1));
        given(meetupRepository.findByStatusAndVoteDeadlineAtLessThanEqual(
                org.mockito.ArgumentMatchers.eq(MeetupStatus.VOTING), any(LocalDateTime.class)))
                .willReturn(List.of(meetup));
        given(recommendationRoundRepository.findFirstByMeetupIdOrderByRoundNoDesc(100L))
                .willReturn(Optional.of(round));
        given(roundCandidateRepository.findByRoundId(2L)).willReturn(List.of(candidate));
        given(voteRepository.countDistinctVotersByRoundId(2L)).willReturn(1L);
        given(voteRepository.countByRoundCandidateId(11L)).willReturn(1L);

        service.resolveExpiredVotes();

        verify(meetupFinalizeService).finalizeInternal(meetup);
    }

    private Meetup withDeadline(LocalDateTime deadline) {
        Meetup deadlineMeetup = Meetup.builder()
                .id(100L).chatRoom(meetup.getChatRoom()).hostUser(meetup.getHostUser()).status(MeetupStatus.VOTING)
                .destinationName(meetup.getDestinationName())
                .destinationLatitude(meetup.getDestinationLatitude())
                .destinationLongitude(meetup.getDestinationLongitude())
                .meetingTime(meetup.getMeetingTime())
                .purpose(meetup.getPurpose())
                .voteDeadlineAt(deadline)
                .build();
        RecommendationRound deadlineRound = RecommendationRound.builder().id(2L).meetup(deadlineMeetup).roundNo(1).build();
        Restaurant restaurant = Restaurant.builder().id(1L).kakaoPlaceId("p1").name("맛집1").build();
        RoundCandidate deadlineCandidate = RoundCandidate.builder().id(11L).round(deadlineRound).restaurant(restaurant).rankNo(1).build();
        lenient().when(recommendationRoundRepository.findById(2L)).thenReturn(Optional.of(deadlineRound));
        lenient().when(roundCandidateRepository.findById(11L)).thenReturn(Optional.of(deadlineCandidate));
        lenient().when(roundResponseAssembler.assemble(deadlineRound))
                .thenReturn(new RoundResponse(100L, 2L, 1, 1, 0, "VOTING", deadline, java.util.List.of()));
        return deadlineMeetup;
    }
}
