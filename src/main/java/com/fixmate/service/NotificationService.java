package com.fixmate.service;

import com.fixmate.dto.NotificationResponse;
import com.fixmate.dto.ReadAllNotificationsResponse;
import com.fixmate.entity.Notification;
import com.fixmate.entity.NotificationType;
import com.fixmate.entity.ServiceRequest;
import com.fixmate.entity.User;
import com.fixmate.event.NotificationCreatedEvent;
import com.fixmate.exception.ResourceNotFoundException;
import com.fixmate.repository.NotificationRepository;
import com.fixmate.repository.UserRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;
    private final ApplicationEventPublisher eventPublisher;

    public NotificationService(
            NotificationRepository notificationRepository,
            UserRepository userRepository,
            ApplicationEventPublisher eventPublisher
    ) {
        this.notificationRepository = notificationRepository;
        this.userRepository = userRepository;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public void notifyRequestOwner(
            ServiceRequest request,
            NotificationType type,
            String title,
            String message
    ) {
        create(request.getUser(), request, type, title, message);
    }

    @Transactional
    public void notifyAssignedProvider(
            ServiceRequest request,
            NotificationType type,
            String title,
            String message
    ) {
        if (request.getAssignedProvider() != null) {
            create(request.getAssignedProvider().getUser(), request, type, title, message);
        }
    }

    @Transactional(readOnly = true)
    public List<NotificationResponse> getNotifications(String userEmail) {
        User user = findUser(userEmail);
        return notificationRepository.findAllByUser_IdOrderByCreatedAtDesc(user.getId()).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public NotificationResponse markRead(Long notificationId, String userEmail) {
        User user = findUser(userEmail);
        Notification notification = notificationRepository.findByIdAndUser_Id(notificationId, user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Notification not found: " + notificationId));
        notification.markRead();
        return toResponse(notification);
    }

    @Transactional
    public ReadAllNotificationsResponse markAllRead(String userEmail) {
        User user = findUser(userEmail);
        int updatedCount = notificationRepository.markAllReadByUserId(user.getId(), Instant.now());
        return new ReadAllNotificationsResponse(updatedCount);
    }

    private void create(
            User recipient,
            ServiceRequest request,
            NotificationType type,
            String title,
            String message
    ) {
        String deduplicationKey = type.name() + ":request:" + request.getId() + ":user:" + recipient.getId();
        if (notificationRepository.existsByDeduplicationKey(deduplicationKey)) {
            return;
        }
        Notification saved = notificationRepository.save(new Notification(
                recipient, request, type, title, message, deduplicationKey));
        eventPublisher.publishEvent(new NotificationCreatedEvent(toResponse(saved)));
    }

    private User findUser(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User account not found"));
    }

    private NotificationResponse toResponse(Notification notification) {
        return new NotificationResponse(
                notification.getId(),
                notification.getType(),
                notification.getTitle(),
                notification.getMessage(),
                notification.getServiceRequest() == null ? null : notification.getServiceRequest().getId(),
                notification.getCreatedAt(),
                notification.getReadAt()
        );
    }
}