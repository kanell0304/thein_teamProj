package com.anything.momeogji.service.chat;

import com.anything.momeogji.dto.chat.ChatRoomResponse;
import com.anything.momeogji.entity.ChatRoom;
import com.anything.momeogji.entity.ChatRoomMember;
import com.anything.momeogji.entity.Member;
import com.anything.momeogji.mapper.chat.ChatRoomQueryMapper;
import com.anything.momeogji.repository.ChatRoomMemberRepository;
import com.anything.momeogji.repository.ChatRoomRepository;
import com.anything.momeogji.repository.MemberRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ChatRoomServiceImplTest {

    @Mock
    private ChatRoomRepository chatRoomRepository;
    @Mock
    private ChatRoomMemberRepository chatRoomMemberRepository;
    @Mock
    private MemberRepository memberRepository;
    @Mock
    private ChatRoomQueryMapper chatRoomQueryMapper;

    @InjectMocks
    private ChatRoomServiceImpl service;

    @Test
    void 방을_만들면_8자리_참여_코드가_발급된다() {
        given(chatRoomRepository.existsByJoinCode(anyString())).willReturn(false);
        given(memberRepository.findById(1L)).willReturn(Optional.of(member(1L)));
        given(chatRoomRepository.save(any())).willAnswer(invocation -> {
            ChatRoom chatRoom = invocation.getArgument(0);
            return ChatRoom.builder().id(100L).name(chatRoom.getName()).joinCode(chatRoom.getJoinCode()).build();
        });

        ChatRoomResponse response = service.createRoom("방 이름", 1L, List.of());

        assertThat(response.joinCode()).matches("[A-HJ-NP-Z2-9]{8}");

        ArgumentCaptor<ChatRoom> captor = ArgumentCaptor.forClass(ChatRoom.class);
        verify(chatRoomRepository).save(captor.capture());
        assertThat(captor.getValue().getJoinCode()).isEqualTo(response.joinCode());
    }

    @Test
    void 코드가_계속_충돌하면_예외를_던진다() {
        given(chatRoomRepository.existsByJoinCode(anyString())).willReturn(true);
        given(memberRepository.findById(1L)).willReturn(Optional.of(member(1L)));

        assertThatThrownBy(() -> service.createRoom("방 이름", 1L, List.of()))
                .isInstanceOf(IllegalStateException.class);

        verify(chatRoomRepository, times(5)).existsByJoinCode(anyString());
        verify(chatRoomRepository, never()).save(any());
    }

    @Test
    void 유효한_코드면_참여자로_등록하고_응답을_반환한다() {
        ChatRoom chatRoom = ChatRoom.builder().id(5L).name("방 이름").joinCode("ABCD2345").build();
        Member member = member(9L);
        given(chatRoomRepository.findByJoinCode("ABCD2345")).willReturn(Optional.of(chatRoom));
        given(chatRoomRepository.findById(5L)).willReturn(Optional.of(chatRoom));
        given(memberRepository.findById(9L)).willReturn(Optional.of(member));
        given(chatRoomMemberRepository.existsByChatRoomAndUser(chatRoom, member)).willReturn(false);

        ChatRoomResponse response = service.joinRoomByCode("ABCD2345", 9L);

        assertThat(response).isEqualTo(new ChatRoomResponse(5L, "방 이름", "ABCD2345"));
        verify(chatRoomMemberRepository).save(any());
    }

    @Test
    void 이미_참여중이면_다시_저장하지_않고_응답만_반환한다() {
        ChatRoom chatRoom = ChatRoom.builder().id(5L).name("방 이름").joinCode("ABCD2345").build();
        Member member = member(9L);
        given(chatRoomRepository.findByJoinCode("ABCD2345")).willReturn(Optional.of(chatRoom));
        given(chatRoomRepository.findById(5L)).willReturn(Optional.of(chatRoom));
        given(memberRepository.findById(9L)).willReturn(Optional.of(member));
        given(chatRoomMemberRepository.existsByChatRoomAndUser(chatRoom, member)).willReturn(true);

        ChatRoomResponse response = service.joinRoomByCode("ABCD2345", 9L);

        assertThat(response).isEqualTo(new ChatRoomResponse(5L, "방 이름", "ABCD2345"));
        verify(chatRoomMemberRepository, never()).save(any());
    }

    @Test
    void 채팅방에서_나가면_현재_회원의_참여관계만_삭제한다() {
        ChatRoom chatRoom = ChatRoom.builder().id(5L).name("방 이름").build();
        ChatRoomMember membership = ChatRoomMember.builder()
                .id(11L)
                .chatRoom(chatRoom)
                .user(member(9L))
                .build();
        given(chatRoomRepository.findById(5L)).willReturn(Optional.of(chatRoom));
        given(chatRoomMemberRepository.findByChatRoomIdAndUserId(5L, 9L)).willReturn(Optional.of(membership));

        service.leaveRoom(5L, 9L);

        verify(chatRoomMemberRepository).delete(membership);
        verify(chatRoomRepository, never()).delete(any());
    }

    @Test
    void 참여하지_않은_채팅방에서는_나갈_수_없다() {
        ChatRoom chatRoom = ChatRoom.builder().id(5L).name("방 이름").build();
        given(chatRoomRepository.findById(5L)).willReturn(Optional.of(chatRoom));
        given(chatRoomMemberRepository.findByChatRoomIdAndUserId(5L, 9L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.leaveRoom(5L, 9L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("참여 중인 채팅방이 아닙니다.");
    }

    @Test
    void 존재하지_않는_코드면_예외() {
        given(chatRoomRepository.findByJoinCode("NOPE0000")).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.joinRoomByCode("NOPE0000", 9L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("유효하지 않은 참여 코드입니다.");
    }

    @Test
    void 코드가_없는_기존_방을_조회하면_코드를_채워_넣는다() {
        ChatRoom legacyRoom = ChatRoom.builder().id(5L).name("예전 방").build();
        given(chatRoomRepository.findById(5L)).willReturn(Optional.of(legacyRoom));
        given(chatRoomRepository.existsByJoinCode(anyString())).willReturn(false);
        given(chatRoomRepository.save(legacyRoom)).willReturn(legacyRoom);

        ChatRoomResponse response = service.getRoom(5L);

        assertThat(response.joinCode()).matches("[A-HJ-NP-Z2-9]{8}");
        verify(chatRoomRepository).save(legacyRoom);
    }

    @Test
    void 이미_코드가_있는_방을_조회하면_다시_발급하지_않는다() {
        ChatRoom chatRoom = ChatRoom.builder().id(5L).name("방 이름").joinCode("ABCD2345").build();
        given(chatRoomRepository.findById(5L)).willReturn(Optional.of(chatRoom));

        ChatRoomResponse response = service.getRoom(5L);

        assertThat(response.joinCode()).isEqualTo("ABCD2345");
        verify(chatRoomRepository, never()).save(any());
    }

    private Member member(Long id) {
        return Member.builder().id(id).kakaoId("kakao-" + id).nickname("멤버" + id).build();
    }
}
