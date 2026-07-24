package com.anything.momeogji.service.chat;

import com.anything.momeogji.entity.ChatMessage;
import org.springframework.stereotype.Component;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/** DB 음식 사전과 누적 음식점명을 이용해 대화의 메뉴 후보를 추출한다. */
@Component
public class ChatMenuKeywordExtractor {

    private static final int NEGATIVE_CONTEXT_LENGTH = 24;
    private static final List<ChatKeywordCandidate.Type> MATCH_PRIORITY = List.of(
            ChatKeywordCandidate.Type.RESTAURANT,
            ChatKeywordCandidate.Type.MENU,
            ChatKeywordCandidate.Type.CATEGORY
    );
    private static final String CLAUSE_BOUNDARIES = ",.!?;。！？\n";
    private static final Pattern NEGATIVE_AFTER_MARKER = Pattern.compile(
            "말고|싫|제외|빼고|안\\s*먹|못\\s*먹|안\\s*땡|별로"
    );
    private static final Pattern NEGATIVE_BEFORE_MARKER = Pattern.compile(
            "(?:"
                    + "안\\s*먹(?:고\\s*싶(?:은|었던)|는|을)"
                    + "|먹고\\s*싶지\\s*않(?:은|는)"
                    + "|못\\s*먹(?:는|을)"
                    + "|안\\s*땡기(?:는|던)"
                    + "|싫어하(?:는|던)"
                    + "|싫은"
                    + "|별로인"
                    + "|제외할"
                    + "|빼고\\s*싶(?:은|었던)"
                    + ")\\s*$"
    );

    public ChatKeywordAnalysisResult extract(
            List<ChatMessage> messages,
            List<ChatKeywordCandidate> candidates
    ) {
        if (messages == null || messages.isEmpty() || candidates == null || candidates.isEmpty()) {
            return ChatKeywordAnalysisResult.empty();
        }

        List<KeywordRule> rules = toRules(candidates);
        if (rules.isEmpty()) {
            return ChatKeywordAnalysisResult.empty();
        }

        Map<KeywordKey, KeywordStat> stats = new LinkedHashMap<>();
        int mentionOrder = 0;
        for (ChatMessage message : messages) {
            if (message == null || message.getContent() == null || message.getContent().isBlank()) {
                continue;
            }

            String normalized = normalize(message.getContent());
            List<KeywordOccurrence> selectedOccurrences = selectOccurrences(
                    normalized,
                    rules
            );

            for (int occurrenceIndex = 0;
                 occurrenceIndex < selectedOccurrences.size();
                 occurrenceIndex++) {
                KeywordOccurrence occurrence = selectedOccurrences.get(occurrenceIndex);
                KeywordRule rule = rules.get(occurrence.ruleOrder());
                KeywordKey key = new KeywordKey(rule.type(), rule.keyword());
                KeywordStat stat = stats.computeIfAbsent(
                        key,
                        ignored -> new KeywordStat(
                                rule.keyword(),
                                rule.type(),
                                rule.ruleOrder()
                        )
                );
                MentionPolarity polarity = classifyPolarity(
                        normalized,
                        selectedOccurrences,
                        occurrenceIndex
                );
                stat.record(polarity, mentionOrder++);
            }
        }

        List<KeywordStat> sortedStats = stats.values().stream()
                .sorted(Comparator
                        .comparingInt(KeywordStat::score).reversed()
                        .thenComparingInt(stat -> resultPriority(stat.type()))
                        .thenComparing(Comparator.comparingInt(
                                KeywordStat::lastPositiveMentionOrder
                        ).reversed())
                        .thenComparingInt(KeywordStat::ruleOrder))
                .toList();

        List<ChatKeywordScore> keywordScores = sortedStats.stream()
                .map(KeywordStat::toScore)
                .toList();

        return new ChatKeywordAnalysisResult(keywordScores);
    }

    private List<KeywordRule> toRules(List<ChatKeywordCandidate> candidates) {
        List<KeywordRule> rules = new ArrayList<>();
        for (ChatKeywordCandidate candidate : candidates) {
            if (candidate == null) {
                continue;
            }

            LinkedHashSet<String> normalizedAliases = new LinkedHashSet<>();
            normalizedAliases.add(normalize(candidate.name()));
            candidate.aliases().stream()
                    .map(this::normalize)
                    .filter(alias -> !alias.isBlank())
                    .forEach(normalizedAliases::add);

            if (!normalizedAliases.isEmpty()) {
                rules.add(new KeywordRule(
                        candidate.name().trim(),
                        candidate.type(),
                        List.copyOf(normalizedAliases),
                        rules.size()
                ));
            }
        }
        return List.copyOf(rules);
    }

    private List<KeywordOccurrence> selectOccurrences(
            String text,
            List<KeywordRule> rules
    ) {
        List<KeywordOccurrence> accepted = new ArrayList<>();
        for (ChatKeywordCandidate.Type type : MATCH_PRIORITY) {
            List<KeywordOccurrence> found = rules.stream()
                    .filter(rule -> rule.type() == type)
                    .flatMap(rule -> findOccurrences(text, rule).stream())
                    .sorted(Comparator
                            .comparingInt(KeywordOccurrence::length).reversed()
                            .thenComparingInt(KeywordOccurrence::start)
                            .thenComparingInt(KeywordOccurrence::ruleOrder))
                    .toList();

            for (KeywordOccurrence occurrence : found) {
                if (accepted.stream().noneMatch(existing -> existing.overlaps(occurrence))) {
                    accepted.add(occurrence);
                }
            }
        }
        return accepted.stream()
                .sorted(Comparator
                        .comparingInt(KeywordOccurrence::start)
                        .thenComparingInt(KeywordOccurrence::end)
                        .thenComparingInt(KeywordOccurrence::ruleOrder))
                .toList();
    }

    private MentionPolarity classifyPolarity(
            String text,
            List<KeywordOccurrence> occurrences,
            int occurrenceIndex
    ) {
        KeywordOccurrence occurrence = occurrences.get(occurrenceIndex);
        int contextStart = Math.max(
                findClauseStart(text, occurrence.start()),
                occurrence.start() - NEGATIVE_CONTEXT_LENGTH
        );
        int contextEnd = Math.min(
                findClauseEnd(text, occurrence.end()),
                occurrence.end() + NEGATIVE_CONTEXT_LENGTH
        );

        if (occurrenceIndex > 0) {
            contextStart = Math.max(
                    contextStart,
                    occurrences.get(occurrenceIndex - 1).end()
            );
        }
        if (occurrenceIndex + 1 < occurrences.size()) {
            contextEnd = Math.min(
                    contextEnd,
                    occurrences.get(occurrenceIndex + 1).start()
            );
        }

        String prefix = text.substring(contextStart, occurrence.start());
        String suffix = text.substring(occurrence.end(), contextEnd);
        if (NEGATIVE_AFTER_MARKER.matcher(suffix).find()
                || NEGATIVE_BEFORE_MARKER.matcher(prefix).find()) {
            return MentionPolarity.NEGATIVE;
        }
        return MentionPolarity.POSITIVE;
    }

    private List<KeywordOccurrence> findOccurrences(String text, KeywordRule rule) {
        List<KeywordOccurrence> occurrences = new ArrayList<>();
        for (String alias : rule.aliases()) {
            int searchFrom = 0;
            int occurrence;
            while ((occurrence = text.indexOf(alias, searchFrom)) >= 0) {
                int aliasEnd = occurrence + alias.length();
                occurrences.add(new KeywordOccurrence(
                        occurrence,
                        aliasEnd,
                        rule.ruleOrder()
                ));
                searchFrom = aliasEnd;
            }
        }
        return occurrences;
    }

    private int findClauseStart(String text, int occurrenceStart) {
        for (int index = occurrenceStart - 1; index >= 0; index--) {
            if (isClauseBoundary(text.charAt(index))) {
                return index + 1;
            }
        }
        return 0;
    }

    private int findClauseEnd(String text, int occurrenceEnd) {
        for (int index = occurrenceEnd; index < text.length(); index++) {
            if (isClauseBoundary(text.charAt(index))) {
                return index;
            }
        }
        return text.length();
    }

    private boolean isClauseBoundary(char character) {
        return CLAUSE_BOUNDARIES.indexOf(character) >= 0;
    }

    private String normalize(String content) {
        return Normalizer.normalize(content, Normalizer.Form.NFC)
                .toLowerCase(Locale.ROOT)
                .replace("\r\n", "\n")
                .replace('\r', '\n')
                .replaceAll("[\\t\\f ]+", " ")
                .replaceAll(" *\\n+ *", "\n")
                .trim();
    }

    private record KeywordRule(
            String keyword,
            ChatKeywordCandidate.Type type,
            List<String> aliases,
            int ruleOrder
    ) {
    }

    private record KeywordOccurrence(int start, int end, int ruleOrder) {

        private int length() {
            return end - start;
        }

        private boolean overlaps(KeywordOccurrence other) {
            return start < other.end && other.start < end;
        }
    }

    private record KeywordKey(
            ChatKeywordCandidate.Type type,
            String keyword
    ) {
    }

    private enum MentionPolarity {
        POSITIVE,
        NEGATIVE
    }

    private static final class KeywordStat {
        private final String keyword;
        private final ChatKeywordCandidate.Type type;
        private final int ruleOrder;
        private int positiveCount;
        private int negativeCount;
        private int lastPositiveMentionOrder = -1;

        private KeywordStat(
                String keyword,
                ChatKeywordCandidate.Type type,
                int ruleOrder
        ) {
            this.keyword = keyword;
            this.type = type;
            this.ruleOrder = ruleOrder;
        }

        private void record(MentionPolarity polarity, int mentionOrder) {
            if (polarity == MentionPolarity.POSITIVE) {
                positiveCount++;
                lastPositiveMentionOrder = mentionOrder;
            } else {
                negativeCount++;
            }
        }

        private int score() {
            return positiveCount - negativeCount;
        }

        private ChatKeywordScore toScore() {
            return new ChatKeywordScore(
                    keyword,
                    type,
                    positiveCount,
                    negativeCount,
                    score()
            );
        }

        private ChatKeywordCandidate.Type type() {
            return type;
        }

        private int lastPositiveMentionOrder() {
            return lastPositiveMentionOrder;
        }

        private int ruleOrder() {
            return ruleOrder;
        }
    }

    private static int resultPriority(ChatKeywordCandidate.Type type) {
        return switch (type) {
            case MENU -> 0;
            case CATEGORY -> 1;
            case RESTAURANT -> 2;
        };
    }
}
