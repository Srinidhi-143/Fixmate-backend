package com.fixmate.dto;

import com.fixmate.entity.RequestPriority;
import com.fixmate.entity.RequestStatus;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;

public record ServiceRequestResponse(

        Long requestId,

        Long userId,

        Long categoryId,

        String categoryName,

        String title,

        String description,

        String address,

        String contactNumber,

        String location,

        Double latitude,

        Double longitude,

        LocalDate preferredDate,

        LocalTime preferredTime,

        RequestPriority priority,

        RequestStatus status,

        Long aiAnalysisId,

        AIAnalysisResult aiAnalysis,

        Long assignedProviderId,

        Instant createdAt,

        Instant updatedAt

) {
}