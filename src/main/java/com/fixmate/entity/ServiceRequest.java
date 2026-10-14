package com.fixmate.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Index;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;

@Entity
@Table(name = "service_requests", indexes = {
        @Index(name = "idx_requests_user_status_created", columnList = "user_id,status,created_at"),
        @Index(name = "idx_requests_category_status", columnList = "category_id,status")
})
public class ServiceRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "category_id", nullable = false)
    private ServiceCategory category;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assigned_provider_id")
    private ServiceProvider assignedProvider;

    @Column(nullable = false, length = 180)
    private String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String description;

    @Column(nullable = false, length = 500)
    private String location;

    @Column(name = "contact_number", nullable = false, length = 20)
    private String contactNumber;

    @Column(name = "client_request_key", length = 64)
    private String clientRequestKey;
    public void setClientRequestKey(String key) { this.clientRequestKey = key; }

    @Column(nullable = false)
    private Double latitude;

    @Column(nullable = false)
    private Double longitude;

    @Column(name = "preferred_date")
    private LocalDate preferredDate;

    @Column(name = "preferred_time")
    private LocalTime preferredTime;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RequestPriority priority = RequestPriority.NORMAL;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private RequestStatus status = RequestStatus.PENDING;

    @Column(name = "ai_analysis_id")
    private Long aiAnalysisId;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    protected ServiceRequest() {
    }

    public ServiceRequest(
            User user,
            ServiceCategory category,
            String title,
            String description,
            String location,
            Double latitude,
            Double longitude,
            LocalDate preferredDate,
            LocalTime preferredTime,
            RequestPriority priority
    ) {
        this.user = user;
        this.category = category;
        updateDetails(title, description, location, latitude, longitude,
                preferredDate, preferredTime, priority);
    }

    public void updateDetails(
            String title,
            String description,
            String location,
            Double latitude,
            Double longitude,
            LocalDate preferredDate,
            LocalTime preferredTime,
            RequestPriority priority
    ) {
        this.title = title;
        this.description = description;
        this.location = location;
        this.latitude = latitude;
        this.longitude = longitude;
        this.preferredDate = preferredDate;
        this.preferredTime = preferredTime;
        this.priority = priority;
    }

    public void setContactNumber(String contactNumber) {
        this.contactNumber = contactNumber;
    }

    public void changeCategory(ServiceCategory category) {
        this.category = category;
    }

    public void changeStatus(RequestStatus status) {
        this.status = status;
    }

    public void applyAIAnalysis(Long analysisId, RequestPriority recommendedPriority) {
        this.aiAnalysisId = analysisId;
        this.priority = recommendedPriority;
        this.status = RequestStatus.AI_ANALYZED;
    }

    public void clearAIAnalysis() {
        this.aiAnalysisId = null;
        this.status = RequestStatus.PENDING;
    }

    public void assignProvider(ServiceProvider provider) {
        this.assignedProvider = provider;
        this.status = RequestStatus.ACCEPTED;
    }

    public void bookProvider(ServiceProvider provider) {
        this.assignedProvider = provider;
        this.status = RequestStatus.REQUESTED;
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public User getUser() {
        return user;
    }

    public ServiceCategory getCategory() {
        return category;
    }

    public ServiceProvider getAssignedProvider() {
        return assignedProvider;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public String getLocation() {
        return location;
    }

    public String getAddress() {
        return location;
    }

    public String getContactNumber() {
        return contactNumber;
    }

    public Double getLatitude() {
        return latitude;
    }

    public Double getLongitude() {
        return longitude;
    }

    public LocalDate getPreferredDate() {
        return preferredDate;
    }

    public LocalTime getPreferredTime() {
        return preferredTime;
    }

    public RequestPriority getPriority() {
        return priority;
    }

    public RequestStatus getStatus() {
        return status;
    }

    public Long getAiAnalysisId() {
        return aiAnalysisId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
