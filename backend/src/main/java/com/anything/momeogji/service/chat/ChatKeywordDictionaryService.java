package com.anything.momeogji.service.chat;

import com.anything.momeogji.entity.FoodKeyword;
import com.anything.momeogji.repository.FoodKeywordRepository;
import com.anything.momeogji.repository.RestaurantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/** 빈 DB 사전과 누적 음식점명을 현재 분석 요청의 후보 목록으로 조합한다. */
@Service
@RequiredArgsConstructor
public class ChatKeywordDictionaryService {

    private final FoodKeywordRepository foodKeywordRepository;
    private final RestaurantRepository restaurantRepository;

    @Transactional(readOnly = true)
    public List<ChatKeywordCandidate> loadCandidates() {
        List<ChatKeywordCandidate> candidates = new ArrayList<>();

        restaurantRepository.findKeywordCandidateNames().stream()
                .map(name -> new ChatKeywordCandidate(
                        ChatKeywordCandidate.Type.RESTAURANT,
                        name,
                        List.of()
                ))
                .forEach(candidates::add);

        foodKeywordRepository.findAllByOrderByIdAsc().stream()
                .map(this::toCandidate)
                .forEach(candidates::add);

        return List.copyOf(candidates);
    }

    private ChatKeywordCandidate toCandidate(FoodKeyword keyword) {
        return new ChatKeywordCandidate(
                ChatKeywordCandidate.Type.valueOf(keyword.getKeywordType().name()),
                keyword.getName(),
                keyword.getAliases()
        );
    }
}
