package com.fixmate.service;

import com.fixmate.dto.CreateReviewRequest;
import com.fixmate.dto.ReviewResponse;
import com.fixmate.entity.RequestStatus;
import com.fixmate.entity.NotificationType;
import com.fixmate.entity.Review;
import com.fixmate.entity.ServiceProvider;
import com.fixmate.entity.ServiceRequest;
import com.fixmate.exception.DuplicateReviewException;
import com.fixmate.exception.ResourceNotFoundException;
import com.fixmate.repository.ReviewRepository;
import com.fixmate.repository.ServiceProviderRepository;
import com.fixmate.repository.ServiceRequestRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

@Service
public class ReviewService {

    private final ReviewRepository reviewRepository;
    private final ServiceRequestRepository requestRepository;
    private final ServiceProviderRepository providerRepository;
    private final NotificationService notificationService;

    public ReviewService(
            ReviewRepository reviewRepository,
            ServiceRequestRepository requestRepository,
            ServiceProviderRepository providerRepository,
            NotificationService notificationService
    ) {
        this.reviewRepository = reviewRepository;
        this.requestRepository = requestRepository;
        this.providerRepository = providerRepository;
        this.notificationService = notificationService;
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public ReviewResponse createReview(String userEmail, CreateReviewRequest request) {
        ServiceRequest serviceRequest = requestRepository.findByIdForUpdate(request.requestId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Service request not found: " + request.requestId()));

        if (!serviceRequest.getUser().getEmail().equalsIgnoreCase(userEmail)) {
            throw new AccessDeniedException("You can only review your own completed service");
        }
        if (serviceRequest.getStatus() != RequestStatus.COMPLETED) {
            throw new IllegalArgumentException("A service can only be reviewed after it is completed");
        }
        if (serviceRequest.getAssignedProvider() == null) {
            throw new IllegalArgumentException("A completed service request must have an assigned provider");
        }
        if (reviewRepository.existsByServiceRequest_Id(serviceRequest.getId())) {
            throw new DuplicateReviewException("This service request has already been reviewed");
        }

        Long providerId = serviceRequest.getAssignedProvider().getId();
        ServiceProvider provider = providerRepository.findByIdForUpdate(providerId)
                .orElseThrow(() -> new ResourceNotFoundException("Provider not found: " + providerId));

        Review review = reviewRepository.save(new Review(
                serviceRequest,
                request.rating(),
                request.review().trim()
        ));

        Double averageRating = reviewRepository.findAverageRatingByProviderId(providerId);
        if (averageRating != null) {
            provider.updateRating(BigDecimal.valueOf(averageRating).setScale(2, RoundingMode.HALF_UP));
        }
        notificationService.notifyAssignedProvider(
            serviceRequest,
            NotificationType.NEW_REVIEW,
            "New service review",
            "You received a " + request.rating() + "-star review."
        );

        return toResponse(review);
    }

    @Transactional(readOnly = true)
    public List<ReviewResponse> getProviderReviews(Long providerId) {
        if (!providerRepository.existsById(providerId)) {
            throw new ResourceNotFoundException("Provider not found: " + providerId);
        }
        return reviewRepository.findAllByServiceRequest_AssignedProvider_IdOrderByCreatedAtDesc(providerId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    private ReviewResponse toResponse(Review review) {
        ServiceRequest serviceRequest = review.getServiceRequest();
        ServiceProvider provider = serviceRequest.getAssignedProvider();
        return new ReviewResponse(
                review.getId(),
                serviceRequest.getId(),
                serviceRequest.getUser().getId(),
                serviceRequest.getUser().getFullName(),
                provider.getId(),
                provider.getUser().getFullName(),
                review.getRating(),
                review.getReview(),
                review.getCreatedAt()
        );
    }
}