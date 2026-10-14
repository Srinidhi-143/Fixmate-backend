package com.fixmate.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record CreateReviewRequest(
        @NotNull @Positive Long requestId,
        @NotNull @Min(1) @Max(5) Integer rating,
        @NotBlank @Size(max = 3000) String review
) {
}