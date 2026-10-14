package com.fixmate.dto;

import com.fixmate.entity.RequestStatus;
import jakarta.validation.constraints.NotNull;

public record RequestStatusRequest(@NotNull RequestStatus status) {
}