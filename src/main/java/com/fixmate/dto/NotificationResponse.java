package com.fixmate.dto;

import com.fixmate.entity.NotificationType;

import java.time.Instant;

public record NotificationResponse(
        Long id,
        NotificationType type,
        String title,
        String message,
        Long requestId,
        Instant createdAt,
        Instant readAt
) {
}