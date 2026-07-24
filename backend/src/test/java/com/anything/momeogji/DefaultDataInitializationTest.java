package com.anything.momeogji;

import com.anything.momeogji.entity.FoodKeyword;
import com.anything.momeogji.entity.recommendation.Restaurant;
import com.anything.momeogji.repository.FoodKeywordRepository;
import com.anything.momeogji.repository.RestaurantRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class DefaultDataInitializationTest {

    private static final Set<String> EXPECTED_KEYWORDS = Set.of(
            "CATEGORY:한식",
            "CATEGORY:중식",
            "CATEGORY:일식",
            "CATEGORY:양식",
            "CATEGORY:아시안",
            "CATEGORY:분식",
            "CATEGORY:카페",
            "CATEGORY:베이커리",
            "CATEGORY:디저트",
            "CATEGORY:패스트푸드",
            "CATEGORY:고기요리",
            "CATEGORY:해산물요리",
            "MENU:초밥",
            "MENU:돈가스",
            "MENU:치킨",
            "MENU:피자",
            "MENU:햄버거",
            "MENU:떡볶이",
            "MENU:김밥",
            "MENU:라면",
            "MENU:국밥",
            "MENU:삼겹살",
            "MENU:닭갈비",
            "MENU:불고기",
            "MENU:제육볶음",
            "MENU:김치찌개",
            "MENU:된장찌개",
            "MENU:비빔밥",
            "MENU:냉면",
            "MENU:칼국수",
            "MENU:만두",
            "MENU:짜장면",
            "MENU:짬뽕",
            "MENU:탕수육",
            "MENU:마라탕",
            "MENU:양꼬치",
            "MENU:파스타",
            "MENU:샐러드",
            "MENU:쌀국수",
            "MENU:카레",
            "MENU:샤브샤브",
            "MENU:우동",
            "MENU:라멘",
            "MENU:족발",
            "MENU:보쌈",
            "MENU:장어구이",
            "MENU:생선회"
    );

    @Autowired
    private FoodKeywordRepository foodKeywordRepository;

    @Autowired
    private RestaurantRepository restaurantRepository;

    @Autowired
    private DataSource dataSource;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void loadsAllDefaultKeywordsAndTheDemonstrationRestaurant() {
        List<FoodKeyword> keywords = foodKeywordRepository.findAllByOrderByIdAsc();
        Set<String> actualKeywordKeys = keywords.stream()
                .map(keyword -> keyword.getKeywordType() + ":" + keyword.getName())
                .collect(Collectors.toSet());

        assertThat(EXPECTED_KEYWORDS).hasSize(47);
        assertThat(actualKeywordKeys).containsAll(EXPECTED_KEYWORDS);
        assertThat(keywords)
                .filteredOn(keyword -> EXPECTED_KEYWORDS.contains(
                        keyword.getKeywordType() + ":" + keyword.getName()
                ))
                .allMatch(keyword -> keyword.getAliases() != null);

        Restaurant restaurant = restaurantRepository.findByKakaoPlaceId("26814353")
                .orElseThrow();
        assertThat(restaurant.getName()).isEqualTo("딸부자네불백 강남역점");
        assertThat(restaurant.getCategory()).isEqualTo("한식");
        assertThat(restaurant.getRoadAddress()).isEqualTo("서울 강남구 봉은사로6길 38");
        assertThat(restaurant.getAddress()).isEqualTo("서울 강남구 역삼동 619");
        assertThat(restaurant.getLatitude()).isEqualByComparingTo("37.5025706");
        assertThat(restaurant.getLongitude()).isEqualByComparingTo("127.0275938");
    }

    @Test
    void canRunDefaultDataScriptAgainWithoutCreatingDuplicates() {
        long keywordCountBefore = foodKeywordRepository.count();
        long restaurantCountBefore = restaurantRepository.count();

        executeDefaultDataScript();

        assertThat(foodKeywordRepository.count()).isEqualTo(keywordCountBefore);
        assertThat(restaurantRepository.count()).isEqualTo(restaurantCountBefore);
    }

    @Test
    @Transactional
    void doesNotOverwriteExistingKeywordAliasesOrRestaurantValues() {
        jdbcTemplate.update("""
                update food_keywords
                set aliases = cast(? as jsonb)
                where keyword_type = 'MENU'
                  and name = '초밥'
                """, "[\"사용자 별칭\"]");
        jdbcTemplate.update("""
                update restaurants
                set name = ?
                where kakao_place_id = '26814353'
                """, "사용자 수정 음식점");

        executeDefaultDataScript();

        Boolean aliasPreserved = jdbcTemplate.queryForObject("""
                select aliases @> '["사용자 별칭"]'::jsonb
                from food_keywords
                where keyword_type = 'MENU'
                  and name = '초밥'
                """, Boolean.class);
        String restaurantName = jdbcTemplate.queryForObject("""
                select name
                from restaurants
                where kakao_place_id = '26814353'
                """, String.class);

        assertThat(aliasPreserved).isTrue();
        assertThat(restaurantName).isEqualTo("사용자 수정 음식점");
    }

    private void executeDefaultDataScript() {
        new ResourceDatabasePopulator(
                new ClassPathResource("db/default-data.sql")
        ).execute(dataSource);
    }
}
