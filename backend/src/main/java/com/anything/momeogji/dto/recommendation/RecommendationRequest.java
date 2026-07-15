package com.anything.momeogji.dto.recommendation;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record RecommendationRequest(
        @NotNull @Valid CommonOptionRequest commonOption,
        @NotEmpty @Valid List<PersonalOptionRequest> personalOptions
) {
}
