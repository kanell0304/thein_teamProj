package com.anything.momeogji.service.chat;

import com.anything.momeogji.entity.ChatMessage;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ChatMenuKeywordExtractorTest {

    private final ChatMenuKeywordExtractor extractor = new ChatMenuKeywordExtractor();

    @Test
    void countsBareProposalAndQuestionMentionsAsPositive() {
        ChatKeywordAnalysisResult result = extractor.extract(
                List.of(
                        message("초밥"),
                        message("초밥 어때?"),
                        message("초밥 먹을까?")
                ),
                List.of(menu("초밥"))
        );

        assertThat(result.menus()).containsExactly("초밥");
        assertThat(result.keywordScores()).containsExactly(
                score("초밥", ChatKeywordCandidate.Type.MENU, 3, 0)
        );
    }

    @Test
    void countsRepeatedMentionsInOneMessageSeparately() {
        ChatKeywordAnalysisResult result = extractor.extract(
                List.of(message("초밥 초밥")),
                List.of(menu("초밥"))
        );

        assertThat(result.keywordScores()).containsExactly(
                score("초밥", ChatKeywordCandidate.Type.MENU, 2, 0)
        );
    }

    @Test
    void recognizesNegativeExpressionsAfterKeyword() {
        ChatKeywordAnalysisResult result = extractor.extract(
                List.of(
                        message("치킨 말고"),
                        message("치킨 싫어"),
                        message("치킨 제외"),
                        message("치킨 빼고"),
                        message("치킨 안 먹어"),
                        message("치킨 못 먹어"),
                        message("치킨 안 땡겨"),
                        message("치킨 별로")
                ),
                List.of(menu("치킨"))
        );

        assertThat(result.menus()).isEmpty();
        assertThat(result.keywordScores()).containsExactly(
                score("치킨", ChatKeywordCandidate.Type.MENU, 0, 8)
        );
    }

    @Test
    void recognizesNegativeModifiersBeforeKeyword() {
        ChatKeywordAnalysisResult result = extractor.extract(
                List.of(
                        message("안 먹고 싶은 치킨"),
                        message("먹고 싶지 않은 치킨"),
                        message("못 먹는 치킨"),
                        message("싫어하는 치킨"),
                        message("별로인 치킨"),
                        message("제외할 치킨"),
                        message("빼고 싶은 치킨")
                ),
                List.of(menu("치킨"))
        );

        assertThat(result.keywordScores()).containsExactly(
                score("치킨", ChatKeywordCandidate.Type.MENU, 0, 7)
        );
    }

    @Test
    void doesNotSpreadNegationToFollowingKeyword() {
        ChatKeywordAnalysisResult result = extractor.extract(
                List.of(
                        message("치킨 말고 피자"),
                        message("치킨은 싫고 피자는 좋아")
                ),
                List.of(
                        menu("치킨"),
                        menu("피자")
                )
        );

        assertThat(result.menus()).containsExactly("피자");
        assertThat(result.keywordScores()).containsExactly(
                score("피자", ChatKeywordCandidate.Type.MENU, 2, 0),
                score("치킨", ChatKeywordCandidate.Type.MENU, 0, 2)
        );
    }

    @Test
    void scoresPositiveAndNegativeOccurrencesOfSameKeywordIndependently() {
        ChatKeywordAnalysisResult result = extractor.extract(
                List.of(message("치킨 말고 치킨 먹자")),
                List.of(menu("치킨"))
        );

        assertThat(result.menus()).isEmpty();
        assertThat(result.keywordScores()).containsExactly(
                score("치킨", ChatKeywordCandidate.Type.MENU, 1, 1)
        );
    }

    @Test
    void includesPositiveNetScoreEvenWhenLatestMentionIsNegative() {
        ChatKeywordAnalysisResult result = extractor.extract(
                List.of(
                        message("초밥"),
                        message("초밥 좋아"),
                        message("초밥 말고")
                ),
                List.of(menu("초밥"))
        );

        assertThat(result.menus()).containsExactly("초밥");
        assertThat(result.keywordScores()).containsExactly(
                score("초밥", ChatKeywordCandidate.Type.MENU, 2, 1)
        );
    }

    @Test
    void returnsPositiveZeroAndNegativeScoresButDisplaysOnlyPositiveNetScore() {
        ChatKeywordAnalysisResult result = extractor.extract(
                List.of(message("피자 치킨 치킨 말고 햄버거 별로")),
                List.of(
                        menu("피자"),
                        menu("치킨"),
                        menu("햄버거")
                )
        );

        assertThat(result.menus()).containsExactly("피자");
        assertThat(result.keywordScores()).containsExactly(
                score("피자", ChatKeywordCandidate.Type.MENU, 1, 0),
                score("치킨", ChatKeywordCandidate.Type.MENU, 1, 1),
                score("햄버거", ChatKeywordCandidate.Type.MENU, 0, 1)
        );
    }

    @Test
    void sortsByNetScoreBeforeTypeAndRecency() {
        ChatKeywordAnalysisResult result = extractor.extract(
                List.of(
                        message("초밥 초밥"),
                        message("일식"),
                        message("스시하루")
                ),
                List.of(
                        restaurant("스시하루"),
                        category("일식"),
                        menu("초밥")
                )
        );

        assertThat(result.menus()).containsExactly("초밥", "일식", "스시하루");
    }

    @Test
    void sortsEqualScoresByMenuCategoryRestaurantTypePriority() {
        ChatKeywordAnalysisResult result = extractor.extract(
                List.of(message("스시하루에서 초밥과 일식 먹자")),
                List.of(
                        restaurant("스시하루"),
                        category("일식"),
                        menu("초밥")
                )
        );

        assertThat(result.menus()).containsExactly("초밥", "일식", "스시하루");
    }

    @Test
    void sortsEqualScoreAndTypeByLatestPositiveMention() {
        ChatKeywordAnalysisResult result = extractor.extract(
                List.of(
                        message("피자"),
                        message("초밥")
                ),
                List.of(
                        menu("피자"),
                        menu("초밥")
                )
        );

        assertThat(result.menus()).containsExactly("초밥", "피자");
    }

    @Test
    void usesCandidateOrderWhenScoresTypeAndPositiveRecencyAreEqual() {
        ChatKeywordAnalysisResult result = extractor.extract(
                List.of(message("피자 말고 초밥 말고")),
                List.of(
                        menu("피자"),
                        menu("초밥")
                )
        );

        assertThat(result.menus()).isEmpty();
        assertThat(result.keywordScores()).containsExactly(
                score("피자", ChatKeywordCandidate.Type.MENU, 0, 1),
                score("초밥", ChatKeywordCandidate.Type.MENU, 0, 1)
        );
    }

    @Test
    void scoresTheExistingDevelopmentChatExample() {
        ChatKeywordAnalysisResult result = extractor.extract(
                List.of(
                        message("오늘 모먹지??"),
                        message("일식이나 초밥 어때요?"),
                        message("치킨은 오늘 말고 돈까스가 좋아요"),
                        message("저도 초밥 좋아요. 모 먹지 써볼까요?")
                ),
                List.of(
                        category("일식"),
                        menu("초밥", "스시"),
                        menu("치킨"),
                        menu("돈가스", "돈까스", "돈카츠")
                )
        );

        assertThat(result.menus()).containsExactly("초밥", "돈가스", "일식");
        assertThat(result.keywordScores()).containsExactly(
                score("초밥", ChatKeywordCandidate.Type.MENU, 2, 0),
                score("돈가스", ChatKeywordCandidate.Type.MENU, 1, 0),
                score("일식", ChatKeywordCandidate.Type.CATEGORY, 1, 0),
                score("치킨", ChatKeywordCandidate.Type.MENU, 0, 1)
        );
    }

    @Test
    void keepsSameNameInDifferentTypesAsSeparateScoresAndOneMenuName() {
        ChatKeywordAnalysisResult result = extractor.extract(
                List.of(message("메뉴별칭 카테고리별칭")),
                List.of(
                        category("공통", "카테고리별칭"),
                        menu("공통", "메뉴별칭")
                )
        );

        assertThat(result.menus()).containsExactly("공통");
        assertThat(result.keywordScores()).containsExactly(
                score("공통", ChatKeywordCandidate.Type.MENU, 1, 0),
                score("공통", ChatKeywordCandidate.Type.CATEGORY, 1, 0)
        );
    }

    @Test
    void restaurantNameBlocksOverlappingFoodAlias() {
        ChatKeywordAnalysisResult result = extractor.extract(
                List.of(message("오늘 스시하루 가는 건 어때요?")),
                List.of(
                        restaurant("스시하루"),
                        menu("초밥", "스시")
                )
        );

        assertThat(result.menus()).containsExactly("스시하루");
        assertThat(result.keywordScores()).containsExactly(
                score("스시하루", ChatKeywordCandidate.Type.RESTAURANT, 1, 0)
        );
    }

    @Test
    void demonstrationRestaurantNameBlocksTheOverlappingBulbaekAlias() {
        ChatKeywordAnalysisResult result = extractor.extract(
                List.of(message("딸부자네불백 강남역점 어때요?")),
                List.of(
                        restaurant("딸부자네불백 강남역점"),
                        menu("불고기", "불백")
                )
        );

        assertThat(result.menus()).containsExactly("딸부자네불백 강남역점");
        assertThat(result.keywordScores()).containsExactly(
                score("딸부자네불백 강남역점", ChatKeywordCandidate.Type.RESTAURANT, 1, 0)
        );
    }

    @Test
    void menuBlocksAnOverlappingCategoryMatch() {
        ChatKeywordAnalysisResult result = extractor.extract(
                List.of(message("초밥 먹을까요?")),
                List.of(
                        category("밥"),
                        menu("초밥")
                )
        );

        assertThat(result.menus()).containsExactly("초밥");
    }

    @Test
    void prefersLongestOverlappingCandidateWithinSameType() {
        ChatKeywordAnalysisResult result = extractor.extract(
                List.of(message("돼지갈비 먹고 싶어요. 스시하루 강남점 가자")),
                List.of(
                        menu("갈비"),
                        menu("돼지갈비"),
                        restaurant("스시하루"),
                        restaurant("스시하루 강남점")
                )
        );

        assertThat(result.menus()).containsExactly("돼지갈비", "스시하루 강남점");
    }

    @Test
    void removesDuplicateNamesAndReturnsAtMostFiveMenus() {
        ChatKeywordAnalysisResult result = extractor.extract(
                List.of(message("한식 중식 일식 양식 피자 초밥")),
                List.of(
                        category("한식"),
                        category("중식"),
                        category("일식"),
                        category("양식"),
                        menu("피자"),
                        menu("초밥")
                )
        );

        assertThat(result.menus())
                .containsExactly("초밥", "피자", "양식", "일식", "중식")
                .doesNotHaveDuplicates();
        assertThat(result.keywordScores()).hasSize(6);
    }

    @Test
    void returnsEmptyAnalysisWhenThereAreNoCandidatesOrMentions() {
        assertThat(extractor.extract(
                List.of(message("초밥 먹을까요?")),
                List.of()
        )).isEqualTo(ChatKeywordAnalysisResult.empty());

        assertThat(extractor.extract(
                List.of(message("몇 시가 좋아요?")),
                List.of(menu("초밥"))
        )).isEqualTo(ChatKeywordAnalysisResult.empty());
    }

    private ChatKeywordScore score(
            String name,
            ChatKeywordCandidate.Type type,
            int positiveCount,
            int negativeCount
    ) {
        return new ChatKeywordScore(
                name,
                type,
                positiveCount,
                negativeCount,
                positiveCount - negativeCount
        );
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
