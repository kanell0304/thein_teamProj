package com.anything.momeogji.service.chat;

import com.anything.momeogji.entity.FoodKeyword;
import com.anything.momeogji.entity.FoodKeywordType;
import com.anything.momeogji.repository.FoodKeywordRepository;
import com.anything.momeogji.repository.RestaurantRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class ChatKeywordDictionaryServiceTest {

    @Mock
    private FoodKeywordRepository foodKeywordRepository;

    @Mock
    private RestaurantRepository restaurantRepository;

    @InjectMocks
    private ChatKeywordDictionaryService service;

    @Test
    void combinesRestaurantNamesAndFoodDictionaryEntries() {
        given(restaurantRepository.findKeywordCandidateNames())
                .willReturn(List.of("스시하루"));
        given(foodKeywordRepository.findAllByOrderByIdAsc())
                .willReturn(List.of(
                        keyword(FoodKeywordType.CATEGORY, "일식", "일본 음식"),
                        keyword(FoodKeywordType.MENU, "초밥", "스시")
                ));

        List<ChatKeywordCandidate> candidates = service.loadCandidates();

        assertThat(candidates).containsExactly(
                new ChatKeywordCandidate(
                        ChatKeywordCandidate.Type.RESTAURANT,
                        "스시하루",
                        List.of()
                ),
                new ChatKeywordCandidate(
                        ChatKeywordCandidate.Type.CATEGORY,
                        "일식",
                        List.of("일본 음식")
                ),
                new ChatKeywordCandidate(
                        ChatKeywordCandidate.Type.MENU,
                        "초밥",
                        List.of("스시")
                )
        );
    }

    @Test
    void returnsEmptyCandidatesWhenBothTablesHaveNoUsableRows() {
        given(restaurantRepository.findKeywordCandidateNames()).willReturn(List.of());
        given(foodKeywordRepository.findAllByOrderByIdAsc()).willReturn(List.of());

        assertThat(service.loadCandidates()).isEmpty();
    }

    private FoodKeyword keyword(FoodKeywordType type, String name, String... aliases) {
        return FoodKeyword.builder()
                .keywordType(type)
                .name(name)
                .aliases(List.of(aliases))
                .build();
    }
}
