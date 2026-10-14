package com.fixmate.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fixmate.entity.RequestPriority;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.time.LocalTime;

public record ServiceRequestRequest(

        @NotNull @Positive Long categoryId,

        @NotBlank @Size(max = 180) String title,

        @NotBlank @Size(max = 5000) String description,

        @JsonAlias("location") @NotBlank @Size(max = 500) String address,

        @NotBlank @Size(max = 20) String contactNumber,

        @NotNull @DecimalMin("-90.0") @DecimalMax("90.0") Double latitude,

        @NotNull @DecimalMin("-180.0") @DecimalMax("180.0") Double longitude,

        @FutureOrPresent LocalDate preferredDate,

        LocalTime preferredTime,

        @NotNull RequestPriority priority

) {
}