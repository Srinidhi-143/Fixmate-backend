package com.fixmate.event;

import com.fixmate.dto.NotificationResponse;

public record NotificationCreatedEvent(NotificationResponse notification) {
}