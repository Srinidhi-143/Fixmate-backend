package com.fixmate.entity;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;

@Entity
@Table(name = "service_providers")
public class ServiceProvider {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "provider_skill_names", joinColumns = @JoinColumn(name = "provider_id"))
    @Column(name = "skill", nullable = false, length = 100)
    private Set<String> skills = new LinkedHashSet<>();

    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(
            name = "provider_skills",
            joinColumns = @JoinColumn(name = "provider_id"),
            inverseJoinColumns = @JoinColumn(name = "category_id"),
            uniqueConstraints = @UniqueConstraint(columnNames = {"provider_id", "category_id"})
    )
    private Set<ServiceCategory> serviceCategories = new LinkedHashSet<>();

    @Column(name = "experience_years", nullable = false)
    private Integer experienceYears;

    @Column(nullable = false, length = 500)
    private String location;

    @Column(nullable = false)
    private Double latitude;

    @Column(nullable = false)
    private Double longitude;

    @Column(name = "service_area_km", nullable = false, columnDefinition = "double default 50.0")
    private Double serviceAreaKm = 50.0;

    @Enumerated(EnumType.STRING)
    @Column(name = "availability_status", nullable = false, length = 20)
    private ProviderAvailability availabilityStatus = ProviderAvailability.AVAILABLE;

    @Column(length = 2000)
    private String description;

    @Column(nullable = false, precision = 3, scale = 2)
    private BigDecimal rating = BigDecimal.ZERO;

    @Column(name = "completed_jobs", nullable = false)
    private Integer completedJobs = 0;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    protected ServiceProvider() {
    }

    public ServiceProvider(
            User user,
            Set<String> skills,
            Set<ServiceCategory> serviceCategories,
            Integer experienceYears,
            String location,
            Double latitude,
            Double longitude,
            Double serviceAreaKm,
            ProviderAvailability availabilityStatus,
            String description
    ) {
        this.user = user;
        updateProfile(skills, serviceCategories, experienceYears, location, latitude, longitude,
                serviceAreaKm, availabilityStatus, description);
    }

    public void updateProfile(
            Set<String> skills,
            Set<ServiceCategory> serviceCategories,
            Integer experienceYears,
            String location,
            Double latitude,
            Double longitude,
            Double serviceAreaKm,
            ProviderAvailability availabilityStatus,
            String description
    ) {
        this.skills.clear();
        this.skills.addAll(skills);
        this.serviceCategories.clear();
        this.serviceCategories.addAll(serviceCategories);
        this.experienceYears = experienceYears;
        this.location = location;
        this.latitude = latitude;
        this.longitude = longitude;
        this.serviceAreaKm = serviceAreaKm;
        this.availabilityStatus = availabilityStatus;
        this.description = description;
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

    public Set<String> getSkills() {
        return skills;
    }

    public Set<ServiceCategory> getServiceCategories() {
        return serviceCategories;
    }

    public Integer getExperienceYears() {
        return experienceYears;
    }

    public String getLocation() {
        return location;
    }

    public Double getLatitude() {
        return latitude;
    }

    public Double getLongitude() {
        return longitude;
    }

    public Double getServiceAreaKm() {
        return serviceAreaKm;
    }

    public ProviderAvailability getAvailabilityStatus() {
        return availabilityStatus;
    }

    public String getDescription() {
        return description;
    }

    public BigDecimal getRating() {
        return rating;
    }

    public void updateRating(BigDecimal rating) {
        this.rating = rating.setScale(2, java.math.RoundingMode.HALF_UP);
    }

    public Integer getCompletedJobs() {
        return completedJobs;
    }

    public void recordCompletedJob() {
        completedJobs++;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}