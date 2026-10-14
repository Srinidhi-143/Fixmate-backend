package com.fixmate.dto;

import com.fixmate.entity.ProviderAvailability;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.util.List;

public record ProviderProfileRequest(
        @NotBlank @Size(max = 150) String name,
        @NotBlank @Pattern(regexp = "\\+?[0-9() -]{7,32}") String phone,
        @NotEmpty List<@NotBlank @Size(max = 100) String> skills,
        @NotNull @Min(0) @Max(80) Integer experienceYears,
        @NotEmpty List<@NotNull @Positive Long> serviceCategoryIds,
        @NotBlank @Size(max = 500) String location,
        @NotNull @DecimalMin("-90.0") @DecimalMax("90.0") Double latitude,
        @NotNull @DecimalMin("-180.0") @DecimalMax("180.0") Double longitude,
        @NotNull @DecimalMin("0.1") @DecimalMax("500.0") Double serviceAreaKm,
        @NotNull ProviderAvailability availabilityStatus,
        @Size(max = 2000) String description
) {
}