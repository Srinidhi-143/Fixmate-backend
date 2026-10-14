package com.fixmate.repository;

import com.fixmate.entity.RequestStatus;
import com.fixmate.entity.ServiceRequest;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ServiceRequestRepository extends JpaRepository<ServiceRequest, Long> {

    Optional<ServiceRequest> findByUser_IdAndClientRequestKey(Long userId, String clientRequestKey);

    @EntityGraph(attributePaths = {"user", "category", "assignedProvider"})
    List<ServiceRequest> findAllByUser_IdOrderByCreatedAtDesc(Long userId);

    @EntityGraph(attributePaths = {"user", "category", "assignedProvider"})
    List<ServiceRequest> findAllByOrderByCreatedAtDesc();

    @EntityGraph(attributePaths = {"user", "category", "assignedProvider"})
    Optional<ServiceRequest> findWithDetailsById(Long id);

    @EntityGraph(attributePaths = {"user", "category", "assignedProvider"})
    List<ServiceRequest> findByCategory_IdAndStatusInAndAssignedProviderIsNullOrderByCreatedAtDesc(
            Long categoryId,
            Collection<RequestStatus> statuses
    );

        @EntityGraph(attributePaths = {"user", "category", "assignedProvider"})
        List<ServiceRequest> findAllByAssignedProvider_IdAndStatusInOrderByUpdatedAtDesc(
            Long providerId,
            Collection<RequestStatus> statuses
        );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select request from ServiceRequest request where request.id = :id")
    Optional<ServiceRequest> findByIdForUpdate(@Param("id") Long id);
}
