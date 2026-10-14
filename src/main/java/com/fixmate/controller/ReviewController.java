package com.fixmate.controller;

import com.fixmate.dto.CreateReviewRequest;
import com.fixmate.dto.ReviewResponse;
import com.fixmate.service.ReviewService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Validated
@RestController
@RequestMapping("/api")
public class ReviewController {

    private final ReviewService reviewService;

    public ReviewController(ReviewService reviewService) {
        this.reviewService = reviewService;
    }

    @PostMapping("/reviews")
    @ResponseStatus(HttpStatus.CREATED)
    public ReviewResponse createReview(
            @AuthenticationPrincipal UserDetails currentUser,
            @Valid @RequestBody CreateReviewRequest request
    ) {
        return reviewService.createReview(currentUser.getUsername(), request);
    }

    @GetMapping("/providers/{providerId}/reviews")
    public List<ReviewResponse> getProviderReviews(@PathVariable @Positive Long providerId) {
        return reviewService.getProviderReviews(providerId);
    }
}