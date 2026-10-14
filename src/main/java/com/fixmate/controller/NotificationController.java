package com.fixmate.controller;

import com.fixmate.dto.NotificationResponse;
import com.fixmate.dto.ReadAllNotificationsResponse;
import com.fixmate.service.NotificationService;
import jakarta.validation.constraints.Positive;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Validated
@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @GetMapping
    public List<NotificationResponse> getNotifications(
            @AuthenticationPrincipal UserDetails currentUser
    ) {
        return notificationService.getNotifications(currentUser.getUsername());
    }

    @PutMapping("/{id}/read")
    public NotificationResponse markRead(
            @PathVariable @Positive Long id,
            @AuthenticationPrincipal UserDetails currentUser
    ) {
        return notificationService.markRead(id, currentUser.getUsername());
    }

    @PutMapping("/read-all")
    public ReadAllNotificationsResponse markAllRead(
            @AuthenticationPrincipal UserDetails currentUser
    ) {
        return notificationService.markAllRead(currentUser.getUsername());
    }
}