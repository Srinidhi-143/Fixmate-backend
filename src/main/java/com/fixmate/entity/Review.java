package com.fixmate.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "reviews", indexes = {
        @Index(name = "idx_reviews_created", columnList = "created_at")
})
public class Review {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "request_id", nullable = false, unique = true)
    private ServiceRequest serviceRequest;

    @Column(nullable = false)
    private Integer rating;

    @Column(nullable = false, length = 3000)
    private String review;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    protected Review() {
    }

    public Review(ServiceRequest serviceRequest, Integer rating, String review) {
        this.serviceRequest = serviceRequest;
        this.rating = rating;
        this.review = review;
    }

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public ServiceRequest getServiceRequest() {
        return serviceRequest;
    }

    public Integer getRating() {
        return rating;
    }

    public String getReview() {
        return review;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}