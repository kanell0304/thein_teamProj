package com.anything.momeogji.dto.recommendation;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RecommendationPreferenceLimitTest {

    private static final ValidatorFactory VALIDATOR_FACTORY =
            Validation.buildDefaultValidatorFactory();
    private static final Validator VALIDATOR = VALIDATOR_FACTORY.getValidator();

    @AfterAll
    static void closeValidatorFactory() {
        VALIDATOR_FACTORY.close();
    }

    @Test
    void rejectsMoreThanThreePreferredKeywordsFromPreferenceSubmission() {
        PreferenceSubmitRequest request = new PreferenceSubmitRequest(
                10,
                List.of("초밥", "일식", "딸부자네불백 강남역점", "돈가스"),
                null,
                false,
                List.of(),
                null,
                false
        );

        assertThat(VALIDATOR.validate(request))
                .extracting(violation -> violation.getPropertyPath().toString())
                .contains("preferredCategories");
    }

    @Test
    void rejectsMoreThanThreePreferredKeywordsFromDirectRoundRequest() {
        PersonalOptionRequest request = new PersonalOptionRequest(
                1L,
                10,
                List.of("초밥", "일식", "딸부자네불백 강남역점", "돈가스"),
                null,
                false,
                List.of(),
                null
        );

        assertThat(VALIDATOR.validate(request))
                .extracting(violation -> violation.getPropertyPath().toString())
                .contains("preferredCategories");
    }

    @Test
    void removesDuplicatePreferredKeywordsWithoutChangingTheirOrder() {
        PreferenceSubmitRequest request = new PreferenceSubmitRequest(
                10,
                List.of("초밥", "일식", "초밥"),
                null,
                false,
                List.of(),
                null,
                false
        );

        assertThat(request.preferredCategories()).containsExactly("초밥", "일식");
        assertThat(VALIDATOR.validate(request)).isEmpty();
    }
}
