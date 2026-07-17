package com.anything.momeogji.service.recommendation;

import com.anything.momeogji.dto.recommendation.FinalNoticeResponse;
import com.anything.momeogji.dto.recommendation.FinalNoticeUpdateRequest;
import com.anything.momeogji.entity.ChatRoom;
import com.anything.momeogji.entity.Member;
import com.anything.momeogji.entity.recommendation.FinalNotice;
import com.anything.momeogji.entity.recommendation.Meetup;
import com.anything.momeogji.entity.recommendation.MeetupStatus;
import com.anything.momeogji.entity.recommendation.RecommendationRound;
import com.anything.momeogji.entity.recommendation.Restaurant;
import com.anything.momeogji.entity.recommendation.RoundCandidate;
import com.anything.momeogji.repository.FinalNoticeChangeLogRepository;
import com.anything.momeogji.repository.FinalNoticeRepository;
import com.anything.momeogji.repository.MeetupParticipantRepository;
import com.anything.momeogji.repository.MeetupRepository;
import com.anything.momeogji.repository.MemberRepository;
import com.anything.momeogji.repository.RecommendationRoundRepository;
import com.anything.momeogji.repository.RoundCandidateRepository;
import com.anything.momeogji.repository.VoteRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/** 동률 랜덤 tie-break를 포함해, 최신 회차 득표수 기준으로 확정하는 로직을 검증한다. */
@ExtendWith(MockitoExtension.class)
class MeetupFinalizeServiceImplTest {

    @Mock
    private MeetupRepository meetupRepository;
    @Mock
    private RecommendationRoundRepository recommendationRoundRepository;
    @Mock
    private RoundCandidateRepository roundCandidateRepository;
    @Mock
    private VoteRepository voteRepository;
    @Mock
    private MeetupParticipantRepository meetupParticipantRepository;
    @Mock
    private MemberRepository memberRepository;
    @Mock
    private FinalNoticeRepository finalNoticeRepository;
    @Mock
    private FinalNoticeChangeLogRepository finalNoticeChangeLogRepository;
    @Mock
    private RecommendationEventPublisher eventPublisher;

    private Meetup meetup;
    private Member host;

    @BeforeEach
    void setUp() {
        ChatRoom chatRoom = ChatRoom.builder().id(10L).name("테스트방").build();
        host = Member.builder().id(1L).kakaoId("k1").nickname("호스트").build();
        meetup = Meetup.builder()
                .id(100L)
                .chatRoom(chatRoom)
                .hostUser(host)
                .status(MeetupStatus.VOTING)
                .destinationName("강남역")
                .destinationLatitude(BigDecimal.valueOf(37.498))
                .destinationLongitude(BigDecimal.valueOf(127.027))
                .meetingTime(LocalDateTime.of(2026, 7, 20, 12, 0))
                .purpose("식사")
                .build();
    }

    @Test
    void 최다득표_후보를_확정한다() {
        MeetupFinalizeServiceImpl service = newService(new Random());

        RecommendationRound round = RecommendationRound.builder().id(2L).meetup(meetup).roundNo(1).build();
        Restaurant r1 = Restaurant.builder().id(1L).kakaoPlaceId("p1").name("맛집1")
                .roadAddress("도로명1").address("지번1")
                .latitude(BigDecimal.valueOf(37.5)).longitude(BigDecimal.valueOf(127.0)).build();
        Restaurant r2 = Restaurant.builder().id(2L).kakaoPlaceId("p2").name("맛집2").build();
        RoundCandidate c1 = RoundCandidate.builder().id(11L).round(round).restaurant(r1).rankNo(1).imageUrl("img1").build();
        RoundCandidate c2 = RoundCandidate.builder().id(12L).round(round).restaurant(r2).rankNo(2).build();

        given(meetupRepository.findById(100L)).willReturn(Optional.of(meetup));
        given(recommendationRoundRepository.findFirstByMeetupIdOrderByRoundNoDesc(100L)).willReturn(Optional.of(round));
        given(roundCandidateRepository.findByRoundId(2L)).willReturn(List.of(c1, c2));
        given(voteRepository.countByRoundCandidateId(11L)).willReturn(3L);
        given(voteRepository.countByRoundCandidateId(12L)).willReturn(1L);
        given(meetupParticipantRepository.countByMeetupId(100L)).willReturn(3L);
        given(finalNoticeRepository.save(any())).willAnswer(invocation -> invocation.getArgument(0));

        FinalNoticeResponse response = service.finalize(100L, 1L);

        assertThat(response.restaurantName()).isEqualTo("맛집1");
        assertThat(response.imageUrl()).isEqualTo("img1");
        assertThat(response.participantCount()).isEqualTo(3);
        assertThat(meetup.getStatus()).isEqualTo(MeetupStatus.FINALIZED);
        verify(finalNoticeRepository).save(any());
        verify(eventPublisher).finalNoticePublished(10L, response);
    }

    @Test
    void 동률이면_랜덤으로_확정한다() {
        Random alwaysSecond = new Random() {
            @Override
            public int nextInt(int bound) {
                return 1;
            }
        };
        MeetupFinalizeServiceImpl service = newService(alwaysSecond);

        RecommendationRound round = RecommendationRound.builder().id(2L).meetup(meetup).roundNo(1).build();
        Restaurant r1 = Restaurant.builder().id(1L).kakaoPlaceId("p1").name("맛집1").build();
        Restaurant r2 = Restaurant.builder().id(2L).kakaoPlaceId("p2").name("맛집2").build();
        RoundCandidate c1 = RoundCandidate.builder().id(11L).round(round).restaurant(r1).rankNo(1).build();
        RoundCandidate c2 = RoundCandidate.builder().id(12L).round(round).restaurant(r2).rankNo(2).build();

        given(meetupRepository.findById(100L)).willReturn(Optional.of(meetup));
        given(recommendationRoundRepository.findFirstByMeetupIdOrderByRoundNoDesc(100L)).willReturn(Optional.of(round));
        given(roundCandidateRepository.findByRoundId(2L)).willReturn(List.of(c1, c2));
        given(voteRepository.countByRoundCandidateId(11L)).willReturn(2L);
        given(voteRepository.countByRoundCandidateId(12L)).willReturn(2L);
        given(meetupParticipantRepository.countByMeetupId(100L)).willReturn(2L);
        given(finalNoticeRepository.save(any())).willAnswer(invocation -> invocation.getArgument(0));

        FinalNoticeResponse response = service.finalize(100L, 1L);

        assertThat(response.restaurantName()).isEqualTo("맛집2");
    }

    @Test
    void 호스트가_아니면_예외() {
        MeetupFinalizeServiceImpl service = newService(new Random());
        given(meetupRepository.findById(100L)).willReturn(Optional.of(meetup));

        assertThatThrownBy(() -> service.finalize(100L, 999L))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 투표가_없으면_예외() {
        MeetupFinalizeServiceImpl service = newService(new Random());

        RecommendationRound round = RecommendationRound.builder().id(2L).meetup(meetup).roundNo(1).build();
        Restaurant r1 = Restaurant.builder().id(1L).kakaoPlaceId("p1").name("맛집1").build();
        RoundCandidate c1 = RoundCandidate.builder().id(11L).round(round).restaurant(r1).rankNo(1).build();

        given(meetupRepository.findById(100L)).willReturn(Optional.of(meetup));
        given(recommendationRoundRepository.findFirstByMeetupIdOrderByRoundNoDesc(100L)).willReturn(Optional.of(round));
        given(roundCandidateRepository.findByRoundId(2L)).willReturn(List.of(c1));
        given(voteRepository.countByRoundCandidateId(11L)).willReturn(0L);

        assertThatThrownBy(() -> service.finalize(100L, 1L))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 약속시간을_수정하면_변경이력을_남기고_재브로드캐스트한다() {
        MeetupFinalizeServiceImpl service = newService(new Random());
        Restaurant restaurant = Restaurant.builder().id(1L).kakaoPlaceId("p1").name("맛집1").build();
        FinalNotice finalNotice = FinalNotice.builder()
                .id(1000L).meetup(meetup).restaurant(restaurant)
                .meetingDatetime(LocalDateTime.of(2026, 7, 20, 12, 0))
                .pinnedUntil(LocalDateTime.of(2026, 7, 20, 12, 0))
                .imageUrl("img1")
                .build();

        given(meetupRepository.findById(100L)).willReturn(Optional.of(meetup));
        given(finalNoticeRepository.findByMeetupId(100L)).willReturn(Optional.of(finalNotice));
        given(memberRepository.findById(1L)).willReturn(Optional.of(host));
        given(meetupParticipantRepository.countByMeetupId(100L)).willReturn(2L);

        LocalDateTime newTime = LocalDateTime.of(2026, 7, 20, 13, 0);
        FinalNoticeResponse response = service.updateFinalNotice(100L, new FinalNoticeUpdateRequest(newTime), 1L);

        assertThat(response.meetingTime()).isEqualTo(newTime);
        assertThat(finalNotice.getMeetingDatetime()).isEqualTo(newTime);
        assertThat(finalNotice.getPinnedUntil()).isEqualTo(newTime);
        verify(finalNoticeChangeLogRepository).save(any());
        verify(eventPublisher).finalNoticePublished(10L, response);
    }

    @Test
    void 같은_시간으로_수정하면_이력을_남기지_않는다() {
        MeetupFinalizeServiceImpl service = newService(new Random());
        Restaurant restaurant = Restaurant.builder().id(1L).kakaoPlaceId("p1").name("맛집1").build();
        LocalDateTime sameTime = LocalDateTime.of(2026, 7, 20, 12, 0);
        FinalNotice finalNotice = FinalNotice.builder()
                .id(1000L).meetup(meetup).restaurant(restaurant)
                .meetingDatetime(sameTime).pinnedUntil(sameTime)
                .build();

        given(meetupRepository.findById(100L)).willReturn(Optional.of(meetup));
        given(finalNoticeRepository.findByMeetupId(100L)).willReturn(Optional.of(finalNotice));
        given(meetupParticipantRepository.countByMeetupId(100L)).willReturn(2L);

        service.updateFinalNotice(100L, new FinalNoticeUpdateRequest(sameTime), 1L);

        verify(finalNoticeChangeLogRepository, never()).save(any());
    }

    @Test
    void 최종공지_수정도_호스트만_가능() {
        MeetupFinalizeServiceImpl service = newService(new Random());
        given(meetupRepository.findById(100L)).willReturn(Optional.of(meetup));

        assertThatThrownBy(() -> service.updateFinalNotice(100L, new FinalNoticeUpdateRequest(LocalDateTime.now()), 999L))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private MeetupFinalizeServiceImpl newService(Random random) {
        return new MeetupFinalizeServiceImpl(meetupRepository, recommendationRoundRepository, roundCandidateRepository,
                voteRepository, meetupParticipantRepository, memberRepository, finalNoticeRepository,
                finalNoticeChangeLogRepository, eventPublisher, random);
    }
}
