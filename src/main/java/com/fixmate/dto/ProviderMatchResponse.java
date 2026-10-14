package com.fixmate.dto;

import com.fixmate.entity.ProviderAvailability;

import java.math.BigDecimal;
import java.util.List;

public record ProviderMatchResponse(
        Long providerId,
        String name,
        List<String> skills,
        Integer experience,
        BigDecimal rating,
        double distance,
        ProviderAvailability availability,
        double matchingScore
) {
}