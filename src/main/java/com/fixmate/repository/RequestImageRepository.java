package com.fixmate.repository;

import com.fixmate.entity.RequestImage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface RequestImageRepository extends JpaRepository<RequestImage, Long> {
    long countByServiceRequest_Id(Long requestId);

    List<RequestImage> findAllByServiceRequest_IdOrderByCreatedAtAsc(Long requestId);

    Optional<RequestImage> findByIdAndServiceRequest_Id(Long id, Long requestId);
}