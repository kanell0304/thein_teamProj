package com.anything.momeogji.dto.chat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ChatMenuKeywordResponseTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void serializesOnlyKeywordScoresUsedByTheClient() throws Exception {
        ChatMenuKeywordResponse response = new ChatMenuKeywordResponse(
                List.of(
                        new ChatMenuKeywordScoreResponse(
                                "초밥",
                                ChatMenuKeywordScoreResponse.KeywordType.MENU,
                                2,
                                1,
                                1
                        ),
                        new ChatMenuKeywordScoreResponse(
                                "치킨",
                                ChatMenuKeywordScoreResponse.KeywordType.MENU,
                                0,
                                1,
                                -1
                        )
                )
        );

        JsonNode json = objectMapper.readTree(objectMapper.writeValueAsBytes(response));

        assertThat(json.get("keywordScores")).hasSize(2);
        assertThat(json.get("keywordScores").get(0).get("name").asText()).isEqualTo("초밥");
        assertThat(json.get("keywordScores").get(0).get("type").asText()).isEqualTo("MENU");
        assertThat(json.get("keywordScores").get(0).get("positiveCount").asInt()).isEqualTo(2);
        assertThat(json.get("keywordScores").get(0).get("negativeCount").asInt()).isEqualTo(1);
        assertThat(json.get("keywordScores").get(0).get("score").asInt()).isEqualTo(1);
        assertThat(json.get("keywordScores").get(1).get("score").asInt()).isEqualTo(-1);
        assertThat(json.has("menus")).isFalse();
        assertThat(json.has("analyzedMessageCount")).isFalse();
    }
}
