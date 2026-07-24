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

    private static final int MAX_KEYWORD_COUNT = 5;
    private static final int NEGATIVE_CONTEXT_LENGTH = 24;
    private static final List<ChatKeywordCandidate.Type> MATCH_PRIORITY = List.of(
            ChatKeywordCandidate.Type.RESTAURANT,
            ChatKeywordCandidate.Type.MENU,
            ChatKeywordCandidate.Type.CATEGORY
    );
    private static final Pattern CLAUSE_BOUNDARY = Pattern.compile("[,.!?;。\\n]");
    private static final Pattern NEGATIVE_MARKER = Pattern.compile(
            "말고|싫|제외|빼고|안\\s*먹|못\\s*먹|안\\s*땡|별로"
    );

    public List<String> extract(
            List<ChatMessage> messages,
            List<ChatKeywordCandidate> candidates
    ) {
        if (messages == null || messages.isEmpty() || candidates == null || candidates.isEmpty()) {
            return List.of();
        }

        List<KeywordRule> rules = toRules(candidates);
        if (rules.isEmpty()) {
            return List.of();
        }

        Map<String, KeywordStat> stats = new LinkedHashMap<>();
        for (int messageOrder = 0; messageOrder < messages.size(); messageOrder++) {
            ChatMessage message = messages.get(messageOrder);
            if (message == null || message.getContent() == null || message.getContent().isBlank()) {
                continue;
            }

            String normalized = normalize(message.getContent());
            List<KeywordOccurrence> selectedOccurrences = selectOccurrences(
                    normalized,
                    rules
            );

            for (KeywordRule rule : rules) {
                MentionPolarity polarity = findPolarity(
                        normalized,
                        rule,
                        selectedOccurrences
                );
                if (polarity == MentionPolarity.NONE) {
                    continue;
                }

                KeywordStat stat = stats.computeIfAbsent(
                        rule.keyword(),
                        ignored -> new KeywordStat(
                                rule.keyword(),
                                rule.type(),
                                rule.ruleOrder()
                        )
                );
                stat.record(
                        polarity,
                        messageOrder,
                        rule.type(),
                        rule.ruleOrder()
                );
            }
        }

        return stats.values().stream()
                .filter(KeywordStat::isPreferred)
                .sorted(Comparator
                        .comparingInt(KeywordStat::positiveMentionCount).reversed()
                        .thenComparingInt(KeywordStat::typePriority)
                        .thenComparing(Comparator.comparingInt(KeywordStat::lastMentionOrder).reversed())
                        .thenComparingInt(KeywordStat::ruleOrder))
                .limit(MAX_KEYWORD_COUNT)
                .map(KeywordStat::keyword)
                .toList();
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
        return List.copyOf(accepted);
    }

    private MentionPolarity findPolarity(
            String text,
            KeywordRule rule,
            List<KeywordOccurrence> selectedOccurrences
    ) {
        List<KeywordOccurrence> occurrences = selectedOccurrences.stream()
                .filter(occurrence -> occurrence.ruleOrder() == rule.ruleOrder())
                .toList();

        if (occurrences.isEmpty()) {
            return MentionPolarity.NONE;
        }
        if (occurrences.stream().anyMatch(occurrence -> isNegated(text, occurrence.end()))) {
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

    private boolean isNegated(String text, int aliasEnd) {
        int maxEnd = Math.min(text.length(), aliasEnd + NEGATIVE_CONTEXT_LENGTH);
        String suffix = text.substring(aliasEnd, maxEnd);
        var boundaryMatcher = CLAUSE_BOUNDARY.matcher(suffix);
        if (boundaryMatcher.find()) {
            suffix = suffix.substring(0, boundaryMatcher.start());
        }
        return NEGATIVE_MARKER.matcher(suffix).find();
    }

    private String normalize(String content) {
        return Normalizer.normalize(content, Normalizer.Form.NFC)
                .toLowerCase(Locale.ROOT)
                .replaceAll("\\s+", " ")
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

    private enum MentionPolarity {
        NONE,
        POSITIVE,
        NEGATIVE
    }

    private static final class KeywordStat {
        private final String keyword;
        private int typePriority;
        private int ruleOrder;
        private int positiveMentionCount;
        private int lastMentionOrder;
        private MentionPolarity lastPolarity;

        private KeywordStat(
                String keyword,
                ChatKeywordCandidate.Type type,
                int ruleOrder
        ) {
            this.keyword = keyword;
            this.typePriority = resultPriority(type);
            this.ruleOrder = ruleOrder;
        }

        private void record(
                MentionPolarity polarity,
                int messageOrder,
                ChatKeywordCandidate.Type type,
                int candidateOrder
        ) {
            if (polarity == MentionPolarity.POSITIVE) {
                positiveMentionCount++;
            }
            updateResultPriority(type, candidateOrder);
            lastPolarity = polarity;
            lastMentionOrder = messageOrder;
        }

        private void updateResultPriority(
                ChatKeywordCandidate.Type type,
                int candidateOrder
        ) {
            int candidateTypePriority = resultPriority(type);
            if (candidateTypePriority < typePriority
                    || (candidateTypePriority == typePriority && candidateOrder < ruleOrder)) {
                typePriority = candidateTypePriority;
                ruleOrder = candidateOrder;
            }
        }

        private boolean isPreferred() {
            return positiveMentionCount > 0 && lastPolarity == MentionPolarity.POSITIVE;
        }

        private String keyword() {
            return keyword;
        }

        private int ruleOrder() {
            return ruleOrder;
        }

        private int typePriority() {
            return typePriority;
        }

        private int positiveMentionCount() {
            return positiveMentionCount;
        }

        private int lastMentionOrder() {
            return lastMentionOrder;
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
