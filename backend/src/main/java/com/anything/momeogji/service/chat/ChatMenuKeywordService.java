package com.anything.momeogji.service.chat;

import com.anything.momeogji.dto.chat.ChatMenuKeywordResponse;
import com.anything.momeogji.dto.chat.ChatMenuKeywordScoreResponse;
import com.anything.momeogji.entity.ChatMessage;
import com.anything.momeogji.repository.ChatMessageRepository;
import com.anything.momeogji.repository.ChatRoomMemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

/** 모먹지 기능 시작 시각을 기준으로 직전 2시간의 채팅 메뉴 키워드를 조회한다. */
@Service
@RequiredArgsConstructor
public class ChatMenuKeywordService {

    private static final ZoneId CHAT_TIME_ZONE = ZoneId.of("Asia/Seoul");
    private static final int ANALYSIS_WINDOW_HOURS = 2;

    private final ChatMessageRepository chatMessageRepository;
    private final ChatRoomMemberRepository chatRoomMemberRepository;
    private final ChatKeywordDictionaryService keywordDictionaryService;
    private final ChatMenuKeywordExtractor keywordExtractor;

    @Transactional(readOnly = true)
    public ChatMenuKeywordResponse extract(
            Long chatRoomId,
            Long memberId,
            Instant featureStartedAt,
            List<Long> participantIds
    ) {
        if (!chatRoomMemberRepository.existsByChatRoomIdAndUserId(chatRoomId, memberId)) {
            throw new AccessDeniedException("채팅방 참여자만 대화 메뉴를 조회할 수 있습니다.");
        }
        if (featureStartedAt == null) {
            throw new IllegalArgumentException("featureStartedAt은 필수입니다.");
        }

        List<Long> distinctParticipantIds = normalizeParticipantIds(participantIds);
        if (!distinctParticipantIds.contains(memberId)) {
            throw new IllegalArgumentException("요청자는 모먹지 참가자에 포함되어야 합니다.");
        }
        if (chatRoomMemberRepository.countByChatRoomIdAndUserIdIn(chatRoomId, distinctParticipantIds)
                != distinctParticipantIds.size()) {
            throw new IllegalArgumentException("모먹지 참가자는 모두 해당 채팅방 참여자여야 합니다.");
        }

        LocalDateTime toExclusive = LocalDateTime.ofInstant(featureStartedAt, CHAT_TIME_ZONE);
        LocalDateTime fromInclusive = toExclusive.minusHours(ANALYSIS_WINDOW_HOURS);
        List<ChatMessage> messages = chatMessageRepository.findParticipantMessagesInPeriod(
                chatRoomId,
                distinctParticipantIds,
                fromInclusive,
                toExclusive
        );

        List<ChatKeywordCandidate> candidates = keywordDictionaryService.loadCandidates();
        ChatKeywordAnalysisResult analysis = keywordExtractor.extract(messages, candidates);
        return new ChatMenuKeywordResponse(
                analysis.menus(),
                analysis.keywordScores().stream()
                        .map(score -> new ChatMenuKeywordScoreResponse(
                                score.name(),
                                ChatMenuKeywordScoreResponse.KeywordType.valueOf(
                                        score.type().name()
                                ),
                                score.positiveCount(),
                                score.negativeCount(),
                                score.score()
                        ))
                        .toList(),
                messages.size()
        );
    }

    private List<Long> normalizeParticipantIds(List<Long> participantIds) {
        if (participantIds == null || participantIds.isEmpty()) {
            throw new IllegalArgumentException("participantIds는 한 명 이상이어야 합니다.");
        }
        if (participantIds.stream().anyMatch(participantId -> participantId == null || participantId <= 0)) {
            throw new IllegalArgumentException("participantIds는 양수여야 합니다.");
        }
        return participantIds.stream().distinct().toList();
    }
}
