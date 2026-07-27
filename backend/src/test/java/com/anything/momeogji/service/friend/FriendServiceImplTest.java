package com.anything.momeogji.service.friend;

import com.anything.momeogji.dto.MemberDTO;
import com.anything.momeogji.dto.friend.FriendRequestResponse;
import com.anything.momeogji.entity.FriendRequest;
import com.anything.momeogji.entity.FriendRequestStatus;
import com.anything.momeogji.entity.Member;
import com.anything.momeogji.repository.FriendRequestRepository;
import com.anything.momeogji.repository.MemberRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class FriendServiceImplTest {

    @Mock
    private FriendRequestRepository friendRequestRepository;
    @Mock
    private MemberRepository memberRepository;

    @InjectMocks
    private FriendServiceImpl service;

    @Test
    void 자기_자신에게는_친구_요청을_보낼_수_없다() {
        assertThatThrownBy(() -> service.sendRequest(1L, 1L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("자기 자신에게는 친구 요청을 보낼 수 없습니다.");
    }

    @Test
    void 이미_내가_보낸_대기중인_요청이_있으면_예외() {
        given(memberRepository.findById(1L)).willReturn(Optional.of(member(1L)));
        given(memberRepository.findById(2L)).willReturn(Optional.of(member(2L)));
        given(friendRequestRepository.findByRequesterIdAndReceiverId(1L, 2L))
                .willReturn(Optional.of(request(1L, 2L, FriendRequestStatus.PENDING)));

        assertThatThrownBy(() -> service.sendRequest(1L, 2L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("이미 요청을 보낸 사용자입니다.");
    }

    @Test
    void 이미_친구인_경우_예외() {
        given(memberRepository.findById(1L)).willReturn(Optional.of(member(1L)));
        given(memberRepository.findById(2L)).willReturn(Optional.of(member(2L)));
        given(friendRequestRepository.findByRequesterIdAndReceiverId(1L, 2L))
                .willReturn(Optional.of(request(1L, 2L, FriendRequestStatus.ACCEPTED)));

        assertThatThrownBy(() -> service.sendRequest(1L, 2L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("이미 친구인 사용자입니다.");
    }

    @Test
    void 상대방이_이미_나에게_요청을_보낸_경우_수락을_안내한다() {
        given(memberRepository.findById(1L)).willReturn(Optional.of(member(1L)));
        given(memberRepository.findById(2L)).willReturn(Optional.of(member(2L)));
        given(friendRequestRepository.findByRequesterIdAndReceiverId(1L, 2L)).willReturn(Optional.empty());
        given(friendRequestRepository.findByRequesterIdAndReceiverId(2L, 1L))
                .willReturn(Optional.of(request(2L, 1L, FriendRequestStatus.PENDING)));

        assertThatThrownBy(() -> service.sendRequest(1L, 2L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("상대방이 이미 나에게 친구 요청을 보냈습니다. 받은 요청에서 수락해주세요.");
    }

    @Test
    void 정상_요청이면_저장된다() {
        given(memberRepository.findById(1L)).willReturn(Optional.of(member(1L)));
        given(memberRepository.findById(2L)).willReturn(Optional.of(member(2L)));
        given(friendRequestRepository.findByRequesterIdAndReceiverId(1L, 2L)).willReturn(Optional.empty());
        given(friendRequestRepository.findByRequesterIdAndReceiverId(2L, 1L)).willReturn(Optional.empty());

        service.sendRequest(1L, 2L);

        verify(friendRequestRepository).save(any());
    }

    @Test
    void 받은_요청_목록을_조회한다() {
        FriendRequest pending = request(2L, 1L, FriendRequestStatus.PENDING);
        given(friendRequestRepository.findByReceiverIdAndStatus(1L, FriendRequestStatus.PENDING))
                .willReturn(List.of(pending));

        List<FriendRequestResponse> responses = service.listReceivedRequests(1L);

        assertThat(responses).containsExactly(
                new FriendRequestResponse(pending.getId(), 2L, "멤버2", null)
        );
    }

    @Test
    void 본인이_받은_요청이_아니면_수락할_수_없다() {
        FriendRequest request = request(2L, 1L, FriendRequestStatus.PENDING);
        given(friendRequestRepository.findById(request.getId())).willReturn(Optional.of(request));

        assertThatThrownBy(() -> service.accept(request.getId(), 99L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("본인이 받은 요청만 수락할 수 있습니다.");
    }

    @Test
    void 수락하면_상태가_바뀐다() {
        FriendRequest request = request(2L, 1L, FriendRequestStatus.PENDING);
        given(friendRequestRepository.findById(request.getId())).willReturn(Optional.of(request));

        service.accept(request.getId(), 1L);

        assertThat(request.getStatus()).isEqualTo(FriendRequestStatus.ACCEPTED);
    }

    @Test
    void 거절하면_삭제된다() {
        FriendRequest request = request(2L, 1L, FriendRequestStatus.PENDING);
        given(friendRequestRepository.findById(request.getId())).willReturn(Optional.of(request));

        service.reject(request.getId(), 1L);

        verify(friendRequestRepository).delete(request);
    }

    @Test
    void 본인이_받은_요청이_아니면_거절할_수_없다() {
        FriendRequest request = request(2L, 1L, FriendRequestStatus.PENDING);
        given(friendRequestRepository.findById(request.getId())).willReturn(Optional.of(request));

        assertThatThrownBy(() -> service.reject(request.getId(), 99L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("본인이 받은 요청만 거절할 수 있습니다.");
        verify(friendRequestRepository, never()).delete(any());
    }

    @Test
    void 친구_목록은_내가_요청자든_수신자든_상대방을_반환한다() {
        FriendRequest iRequested = request(1L, 2L, FriendRequestStatus.ACCEPTED);
        FriendRequest theyRequested = request(3L, 1L, FriendRequestStatus.ACCEPTED);
        given(friendRequestRepository.findAcceptedByMemberId(1L)).willReturn(List.of(iRequested, theyRequested));

        List<MemberDTO> friends = service.listFriends(1L);

        assertThat(friends).extracting(MemberDTO::id).containsExactlyInAnyOrder(2L, 3L);
    }

    private FriendRequest request(Long requesterId, Long receiverId, FriendRequestStatus status) {
        return FriendRequest.builder()
                .id(requesterId * 100 + receiverId)
                .requester(member(requesterId))
                .receiver(member(receiverId))
                .status(status)
                .build();
    }

    private Member member(Long id) {
        return Member.builder().id(id).kakaoId("kakao-" + id).nickname("멤버" + id).build();
    }
}
