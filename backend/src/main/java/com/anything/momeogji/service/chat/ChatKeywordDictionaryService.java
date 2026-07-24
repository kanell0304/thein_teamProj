package com.anything.momeogji.service.chat;

import com.anything.momeogji.entity.FoodKeyword;
import com.anything.momeogji.repository.FoodKeywordRepository;
import com.anything.momeogji.repository.RestaurantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/** 음식 사전을 한 번 조회해 유형별로 나눈 뒤 누적 음식점명을 결합한다. */
@Service
@RequiredArgsConstructor
public class ChatKeywordDictionaryService {

    private final FoodKeywordRepository foodKeywordRepository;
    private final RestaurantRepository restaurantRepository;

    @Transactional(readOnly = true)
    public List<ChatKeywordCandidate> loadCandidates() {
        List<ChatKeywordCandidate> candidates = new ArrayList<>();

        List<ChatKeywordCandidate> foodCandidates = foodKeywordRepository
                .findAllByOrderByIdAsc()
                .stream()
                .map(this::toCandidate)
                .toList();

        addType(candidates, foodCandidates, ChatKeywordCandidate.Type.MENU);
        addType(candidates, foodCandidates, ChatKeywordCandidate.Type.CATEGORY);

        restaurantRepository.findKeywordCandidateNames().stream()
                .map(name -> new ChatKeywordCandidate(
                        ChatKeywordCandidate.Type.RESTAURANT,
                        name,
                        List.of()
                ))
                .forEach(candidates::add);

        return List.copyOf(candidates);
    }

    private void addType(
            List<ChatKeywordCandidate> target,
            List<ChatKeywordCandidate> candidates,
            ChatKeywordCandidate.Type type
    ) {
        candidates.stream()
                .filter(candidate -> candidate.type() == type)
                .forEach(target::add);
    }

    private ChatKeywordCandidate toCandidate(FoodKeyword keyword) {
        return new ChatKeywordCandidate(
                ChatKeywordCandidate.Type.valueOf(keyword.getKeywordType().name()),
                keyword.getName(),
                keyword.getAliases()
        );
    }
}
