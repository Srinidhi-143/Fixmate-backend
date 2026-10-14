package com.fixmate.dto;

import java.time.Instant;

public record ReviewResponse(
        Long reviewId,
        Long requestId,
        Long userId,
        String userName,
        Long providerId,
        String providerName,
        Integer rating,
        String review,
        Instant createdAt
) {
}