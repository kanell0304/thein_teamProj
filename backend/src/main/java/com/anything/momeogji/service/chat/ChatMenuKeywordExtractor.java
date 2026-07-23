package com.anything.momeogji.service.chat;

import com.anything.momeogji.entity.ChatMessage;
import org.springframework.stereotype.Component;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/** 외부 AI 없이 한정된 음식·메뉴 사전과 부정 표현 규칙으로 대화의 메뉴 후보를 추출한다. */
@Component
public class ChatMenuKeywordExtractor {

    private static final int MAX_KEYWORD_COUNT = 5;
    private static final int NEGATIVE_CONTEXT_LENGTH = 24;
    private static final Pattern CLAUSE_BOUNDARY = Pattern.compile("[,.!?;。\\n]");
    private static final Pattern NEGATIVE_MARKER = Pattern.compile(
            "말고|싫|제외|빼고|안\\s*먹|못\\s*먹|안\\s*땡|별로"
    );
    private static final List<MenuRule> MENU_RULES = List.of(
            rule("한식", "한식", "한정식"),
            rule("중식", "중식", "중국집"),
            rule("일식", "일식", "일본 음식"),
            rule("양식", "양식", "서양 음식"),
            rule("돈까스", "돈까스", "돈가스", "돈카츠"),
            rule("파스타", "파스타", "스파게티"),
            rule("초밥", "초밥", "스시"),
            rule("치킨", "치킨", "닭튀김"),
            rule("햄버거", "햄버거", "버거"),
            rule("떡볶이", "떡볶이"),
            rule("피자", "피자"),
            rule("국밥", "국밥"),
            rule("삼겹살", "삼겹살"),
            rule("족발", "족발"),
            rule("보쌈", "보쌈"),
            rule("냉면", "냉면"),
            rule("라멘", "라멘", "라면"),
            rule("우동", "우동"),
            rule("쌀국수", "쌀국수"),
            rule("마라탕", "마라탕"),
            rule("샤브샤브", "샤브샤브"),
            rule("짜장면", "짜장면", "자장면"),
            rule("짬뽕", "짬뽕"),
            rule("곱창", "곱창")
    );

    public List<String> extract(List<ChatMessage> messages) {
        if (messages == null || messages.isEmpty()) {
            return List.of();
        }

        Map<String, KeywordStat> stats = new LinkedHashMap<>();
        for (int messageOrder = 0; messageOrder < messages.size(); messageOrder++) {
            ChatMessage message = messages.get(messageOrder);
            if (message == null || message.getContent() == null || message.getContent().isBlank()) {
                continue;
            }

            String normalized = normalize(message.getContent());
            for (int ruleOrder = 0; ruleOrder < MENU_RULES.size(); ruleOrder++) {
                MenuRule rule = MENU_RULES.get(ruleOrder);
                int currentRuleOrder = ruleOrder;
                MentionPolarity polarity = findPolarity(normalized, rule);
                if (polarity == MentionPolarity.NONE) {
                    continue;
                }

                KeywordStat stat = stats.computeIfAbsent(
                        rule.keyword(),
                        ignored -> new KeywordStat(rule.keyword(), currentRuleOrder)
                );
                stat.record(polarity, messageOrder);
            }
        }

        return stats.values().stream()
                .filter(KeywordStat::isPreferred)
                .sorted(Comparator
                        .comparingInt(KeywordStat::positiveMentionCount).reversed()
                        .thenComparing(Comparator.comparingInt(KeywordStat::lastMentionOrder).reversed())
                        .thenComparingInt(KeywordStat::ruleOrder))
                .limit(MAX_KEYWORD_COUNT)
                .map(KeywordStat::keyword)
                .toList();
    }

    private MentionPolarity findPolarity(String text, MenuRule rule) {
        boolean foundPositive = false;
        for (String alias : rule.aliases()) {
            int searchFrom = 0;
            int occurrence;
            while ((occurrence = text.indexOf(alias, searchFrom)) >= 0) {
                int aliasEnd = occurrence + alias.length();
                if (isNegated(text, aliasEnd)) {
                    return MentionPolarity.NEGATIVE;
                }
                foundPositive = true;
                searchFrom = aliasEnd;
            }
        }
        return foundPositive ? MentionPolarity.POSITIVE : MentionPolarity.NONE;
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

    private static MenuRule rule(String keyword, String... aliases) {
        List<String> normalizedAliases = new ArrayList<>(aliases.length);
        for (String alias : aliases) {
            normalizedAliases.add(alias.toLowerCase(Locale.ROOT));
        }
        return new MenuRule(keyword, List.copyOf(normalizedAliases));
    }

    private record MenuRule(String keyword, List<String> aliases) {
    }

    private enum MentionPolarity {
        NONE,
        POSITIVE,
        NEGATIVE
    }

    private static final class KeywordStat {
        private final String keyword;
        private final int ruleOrder;
        private int positiveMentionCount;
        private int lastMentionOrder;
        private MentionPolarity lastPolarity;

        private KeywordStat(String keyword, int ruleOrder) {
            this.keyword = keyword;
            this.ruleOrder = ruleOrder;
        }

        private void record(MentionPolarity polarity, int messageOrder) {
            if (polarity == MentionPolarity.POSITIVE) {
                positiveMentionCount++;
            }
            lastPolarity = polarity;
            lastMentionOrder = messageOrder;
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

        private int positiveMentionCount() {
            return positiveMentionCount;
        }

        private int lastMentionOrder() {
            return lastMentionOrder;
        }
    }
}
