package com.fixmate.dto;

import com.fixmate.entity.UserRole;

import java.time.Instant;

public record AdminUserResponse(
        Long id,
        String name,
        String email,
        String phone,
        UserRole role,
        boolean active,
        Instant createdAt
) {
}