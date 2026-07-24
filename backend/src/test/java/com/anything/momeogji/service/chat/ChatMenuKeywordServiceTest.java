package com.anything.momeogji.service.chat;

import com.anything.momeogji.dto.chat.ChatMenuKeywordResponse;
import com.anything.momeogji.entity.ChatMessage;
import com.anything.momeogji.repository.ChatMessageRepository;
import com.anything.momeogji.repository.ChatRoomMemberRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class ChatMenuKeywordServiceTest {

    @Mock
    private ChatMessageRepository chatMessageRepository;

    @Mock
    private ChatRoomMemberRepository chatRoomMemberRepository;

    @Mock
    private ChatMenuKeywordExtractor keywordExtractor;

    @InjectMocks
    private ChatMenuKeywordService service;

    @Test
    void queriesTheTwoHourWindowWithInclusiveStartAndExclusiveEnd() {
        Long chatRoomId = 7L;
        Long memberId = 11L;
        Instant featureStartedAt = Instant.parse("2026-07-22T12:00:00Z");
        LocalDateTime fromInclusive = LocalDateTime.of(2026, 7, 22, 19, 0);
        LocalDateTime toExclusive = LocalDateTime.of(2026, 7, 22, 21, 0);
        List<ChatMessage> messages = List.of(message("초밥"), message("스시"));

        given(chatRoomMemberRepository.existsByChatRoomIdAndUserId(chatRoomId, memberId))
                .willReturn(true);
        given(chatRoomMemberRepository.countByChatRoomIdAndUserIdIn(chatRoomId, List.of(11L, 12L)))
                .willReturn(2L);
        given(chatMessageRepository.findParticipantMessagesInPeriod(
                chatRoomId,
                List.of(11L, 12L),
                fromInclusive,
                toExclusive
        )).willReturn(messages);
        given(keywordExtractor.extract(messages)).willReturn(List.of("초밥"));

        ChatMenuKeywordResponse response = service.extract(
                chatRoomId,
                memberId,
                featureStartedAt,
                List.of(11L, 12L, 12L)
        );

        assertThat(response.menus()).containsExactly("초밥");
        assertThat(response.analyzedMessageCount()).isEqualTo(2);
        verify(chatMessageRepository).findParticipantMessagesInPeriod(
                chatRoomId,
                List.of(11L, 12L),
                fromInclusive,
                toExclusive
        );
    }

    @Test
    void rejectsMemberWhoDoesNotBelongToTheChatRoom() {
        given(chatRoomMemberRepository.existsByChatRoomIdAndUserId(7L, 11L))
                .willReturn(false);

        assertThatThrownBy(() -> service.extract(
                7L,
                11L,
                Instant.parse("2026-07-22T12:00:00Z"),
                List.of(11L)
        )).isInstanceOf(AccessDeniedException.class);

        verifyNoInteractions(chatMessageRepository, keywordExtractor);
    }

    @Test
    void rejectsRequestWhenCallerIsNotAMomeokjiParticipant() {
        given(chatRoomMemberRepository.existsByChatRoomIdAndUserId(7L, 11L))
                .willReturn(true);

        assertThatThrownBy(() -> service.extract(
                7L,
                11L,
                Instant.parse("2026-07-22T12:00:00Z"),
                List.of(12L)
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("요청자는 모먹지 참가자에 포함되어야 합니다.");

        verifyNoInteractions(chatMessageRepository, keywordExtractor);
    }

    @Test
    void rejectsParticipantWhoDoesNotBelongToTheChatRoom() {
        given(chatRoomMemberRepository.existsByChatRoomIdAndUserId(7L, 11L))
                .willReturn(true);
        given(chatRoomMemberRepository.countByChatRoomIdAndUserIdIn(7L, List.of(11L, 99L)))
                .willReturn(1L);

        assertThatThrownBy(() -> service.extract(
                7L,
                11L,
                Instant.parse("2026-07-22T12:00:00Z"),
                List.of(11L, 99L)
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("모먹지 참가자는 모두 해당 채팅방 참여자여야 합니다.");

        verifyNoInteractions(chatMessageRepository, keywordExtractor);
    }

    @Test
    void rejectsEmptyParticipantIds() {
        given(chatRoomMemberRepository.existsByChatRoomIdAndUserId(7L, 11L))
                .willReturn(true);

        assertThatThrownBy(() -> service.extract(
                7L,
                11L,
                Instant.parse("2026-07-22T12:00:00Z"),
                List.of()
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("participantIds는 한 명 이상이어야 합니다.");

        verifyNoInteractions(chatMessageRepository, keywordExtractor);
    }

    @Test
    void rejectsNonPositiveParticipantId() {
        given(chatRoomMemberRepository.existsByChatRoomIdAndUserId(7L, 11L))
                .willReturn(true);

        assertThatThrownBy(() -> service.extract(
                7L,
                11L,
                Instant.parse("2026-07-22T12:00:00Z"),
                List.of(11L, 0L)
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("participantIds는 양수여야 합니다.");

        verifyNoInteractions(chatMessageRepository, keywordExtractor);
    }

    private ChatMessage message(String content) {
        return ChatMessage.builder()
                .content(content)
                .build();
    }
}
