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
        given(chatMessageRepository.findUserMessagesInPeriod(
                chatRoomId,
                fromInclusive,
                toExclusive
        )).willReturn(messages);
        given(keywordExtractor.extract(messages)).willReturn(List.of("초밥"));

        ChatMenuKeywordResponse response = service.extract(
                chatRoomId,
                memberId,
                featureStartedAt
        );

        assertThat(response.menus()).containsExactly("초밥");
        assertThat(response.analyzedMessageCount()).isEqualTo(2);
        verify(chatMessageRepository).findUserMessagesInPeriod(
                chatRoomId,
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
                Instant.parse("2026-07-22T12:00:00Z")
        )).isInstanceOf(AccessDeniedException.class);

        verifyNoInteractions(chatMessageRepository, keywordExtractor);
    }

    private ChatMessage message(String content) {
        return ChatMessage.builder()
                .content(content)
                .build();
    }
}
