package com.anything.momeogji.service.chat;

import com.anything.momeogji.entity.ChatMessage;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ChatMenuKeywordExtractorTest {

    private final ChatMenuKeywordExtractor extractor = new ChatMenuKeywordExtractor();

    @Test
    void extractsAliasesAndRanksRepeatedMenusFirst() {
        List<String> menus = extractor.extract(List.of(
                message("스시 먹는 건 어때요?"),
                message("돈가스도 괜찮아요"),
                message("저도 초밥 좋아요")
        ));

        assertThat(menus).containsExactly("초밥", "돈까스");
    }

    @Test
    void excludesMenuWhenItsLatestMentionIsNegative() {
        List<String> menus = extractor.extract(List.of(
                message("치킨 먹고 싶어요"),
                message("치킨은 오늘 말고 돈카츠가 좋아요")
        ));

        assertThat(menus).containsExactly("돈까스");
    }

    @Test
    void restoresMenuWhenAPositiveMentionComesAfterTheNegativeMention() {
        List<String> menus = extractor.extract(List.of(
                message("오늘은 치킨 말고요"),
                message("생각해 보니 치킨 좋아요")
        ));

        assertThat(menus).containsExactly("치킨");
    }

    @Test
    void removesDuplicatesAndReturnsAtMostFiveMenus() {
        List<String> menus = extractor.extract(List.of(
                message("한식 한식 중식 일식 양식 피자 초밥")
        ));

        assertThat(menus)
                .hasSize(5)
                .doesNotHaveDuplicates();
    }

    @Test
    void returnsEmptyListWhenThereIsNoMenuMention() {
        assertThat(extractor.extract(List.of(message("다들 몇 시가 좋아요?"))))
                .isEmpty();
    }

    private ChatMessage message(String content) {
        return ChatMessage.builder()
                .content(content)
                .build();
    }
}
