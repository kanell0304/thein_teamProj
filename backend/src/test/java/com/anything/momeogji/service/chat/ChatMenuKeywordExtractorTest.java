package com.anything.momeogji.service.chat;

import com.anything.momeogji.entity.ChatMessage;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ChatMenuKeywordExtractorTest {

    private final ChatMenuKeywordExtractor extractor = new ChatMenuKeywordExtractor();

    @Test
    void extractsNamesAndAliasesSuppliedByTheDatabaseDictionary() {
        List<String> menus = extractor.extract(
                List.of(
                        message("스시 먹는 건 어때요?"),
                        message("돈가스도 괜찮아요"),
                        message("저도 초밥 좋아요")
                ),
                List.of(
                        menu("초밥", "스시"),
                        menu("돈까스", "돈가스", "돈카츠")
                )
        );

        assertThat(menus).containsExactly("초밥", "돈까스");
    }

    @Test
    void excludesMenuWhenItsLatestMentionIsNegative() {
        List<String> menus = extractor.extract(
                List.of(
                        message("치킨 먹고 싶어요"),
                        message("치킨은 오늘 말고 돈카츠가 좋아요")
                ),
                List.of(
                        menu("치킨"),
                        menu("돈까스", "돈가스", "돈카츠")
                )
        );

        assertThat(menus).containsExactly("돈까스");
    }

    @Test
    void restoresMenuWhenAPositiveMentionComesAfterTheNegativeMention() {
        List<String> menus = extractor.extract(
                List.of(
                        message("오늘은 치킨 말고요"),
                        message("생각해 보니 치킨 좋아요")
                ),
                List.of(menu("치킨"))
        );

        assertThat(menus).containsExactly("치킨");
    }

    @Test
    void removesDuplicatesAndReturnsAtMostFiveMenus() {
        List<String> menus = extractor.extract(
                List.of(message("한식 한식 중식 일식 양식 피자 초밥")),
                List.of(
                        category("한식"),
                        category("중식"),
                        category("일식"),
                        category("양식"),
                        menu("피자"),
                        menu("초밥")
                )
        );

        assertThat(menus)
                .hasSize(5)
                .doesNotHaveDuplicates();
    }

    @Test
    void extractsRestaurantNameWhenFoodDictionaryIsEmpty() {
        List<String> menus = extractor.extract(
                List.of(message("오늘 스시하루 가는 건 어때요?")),
                List.of(restaurant("스시하루"))
        );

        assertThat(menus).containsExactly("스시하루");
    }

    @Test
    void restaurantNameBlocksOverlappingFoodAlias() {
        List<String> menus = extractor.extract(
                List.of(message("오늘 스시하루 가는 건 어때요?")),
                List.of(
                        restaurant("스시하루"),
                        menu("초밥", "스시")
                )
        );

        assertThat(menus).containsExactly("스시하루");
    }

    @Test
    void continuesMatchingNonOverlappingMenuCategoryAndRestaurantMentions() {
        List<String> menus = extractor.extract(
                List.of(message("스시하루에서 초밥과 일식 먹는 건 어때요?")),
                List.of(
                        category("일식"),
                        restaurant("스시하루"),
                        menu("초밥")
                )
        );

        assertThat(menus).containsExactly("초밥", "일식", "스시하루");
    }

    @Test
    void menuBlocksAnOverlappingCategoryMatch() {
        List<String> menus = extractor.extract(
                List.of(message("초밥 먹을까요?")),
                List.of(
                        category("밥"),
                        menu("초밥")
                )
        );

        assertThat(menus).containsExactly("초밥");
    }

    @Test
    void prefersTheLongestOverlappingMenuName() {
        List<String> menus = extractor.extract(
                List.of(message("돼지갈비 먹고 싶어요")),
                List.of(
                        menu("갈비"),
                        menu("돼지갈비")
                )
        );

        assertThat(menus).containsExactly("돼지갈비");
    }

    @Test
    void prefersTheLongestOverlappingRestaurantName() {
        List<String> menus = extractor.extract(
                List.of(message("스시하루 강남점 가자")),
                List.of(
                        restaurant("스시하루"),
                        restaurant("스시하루 강남점")
                )
        );

        assertThat(menus).containsExactly("스시하루 강남점");
    }

    @Test
    void usesMenuCategoryRestaurantOrderBeforeRecencyWhenScoresAreEqual() {
        List<String> menus = extractor.extract(
                List.of(
                        message("초밥 좋아요"),
                        message("일식 좋아요"),
                        message("스시하루 좋아요")
                ),
                List.of(
                        restaurant("스시하루"),
                        category("일식"),
                        menu("초밥")
                )
        );

        assertThat(menus).containsExactly("초밥", "일식", "스시하루");
    }

    @Test
    void returnsEmptyListWhenDictionaryAndRestaurantCandidatesAreEmpty() {
        assertThat(extractor.extract(
                List.of(message("초밥 먹을까요?")),
                List.of()
        )).isEmpty();
    }

    @Test
    void returnsEmptyListWhenThereIsNoKeywordMention() {
        assertThat(extractor.extract(
                List.of(message("다들 몇 시가 좋아요?")),
                List.of(menu("초밥", "스시"))
        )).isEmpty();
    }

    private ChatKeywordCandidate category(String name, String... aliases) {
        return candidate(ChatKeywordCandidate.Type.CATEGORY, name, aliases);
    }

    private ChatKeywordCandidate menu(String name, String... aliases) {
        return candidate(ChatKeywordCandidate.Type.MENU, name, aliases);
    }

    private ChatKeywordCandidate restaurant(String name) {
        return candidate(ChatKeywordCandidate.Type.RESTAURANT, name);
    }

    private ChatKeywordCandidate candidate(
            ChatKeywordCandidate.Type type,
            String name,
            String... aliases
    ) {
        return new ChatKeywordCandidate(type, name, List.of(aliases));
    }

    private ChatMessage message(String content) {
        return ChatMessage.builder()
                .content(content)
                .build();
    }
}
