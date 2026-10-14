package com.fixmate.repository;

import com.fixmate.entity.AIAnalysis;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AIAnalysisRepository extends JpaRepository<AIAnalysis, Long> {
    Optional<AIAnalysis> findByIdAndServiceRequest_Id(Long id, Long requestId);
}