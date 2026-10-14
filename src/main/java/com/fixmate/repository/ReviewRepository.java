package com.fixmate.repository;

import com.fixmate.entity.Review;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ReviewRepository extends JpaRepository<Review, Long> {

    boolean existsByServiceRequest_Id(Long requestId);

    @EntityGraph(attributePaths = {
            "serviceRequest",
            "serviceRequest.user",
            "serviceRequest.assignedProvider",
            "serviceRequest.assignedProvider.user"
    })
    List<Review> findAllByServiceRequest_AssignedProvider_IdOrderByCreatedAtDesc(Long providerId);

    @Query("select avg(review.rating) from Review review "
            + "where review.serviceRequest.assignedProvider.id = :providerId")
    Double findAverageRatingByProviderId(@Param("providerId") Long providerId);
}