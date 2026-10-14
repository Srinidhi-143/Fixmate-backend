package com.fixmate.dto;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
public record ProviderBookingRequest(@NotNull @Positive Long providerId) {}
