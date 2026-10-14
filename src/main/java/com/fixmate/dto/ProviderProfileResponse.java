package com.fixmate.dto;

import com.fixmate.entity.ProviderAvailability;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Set;

public record ProviderProfileResponse(
        Long providerId,
        Long userId,
        String name,
        String phone,
        Set<String> skills,
        Integer experienceYears,
        List<ProviderCategoryResponse> serviceCategories,
        String location,
        Double latitude,
        Double longitude,
        Double serviceAreaKm,
        ProviderAvailability availabilityStatus,
        String description,
        BigDecimal rating,
        Integer completedJobs,
        Instant createdAt,
        Instant updatedAt
) {
}