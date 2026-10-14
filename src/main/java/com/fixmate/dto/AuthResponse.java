package com.fixmate.dto;

import com.fixmate.entity.UserRole;

public record AuthResponse(
        String accessToken,
        String tokenType,
        long expiresInSeconds,
        Long userId,
        String email,
        UserRole role,
        String name,
        String phone
) {
}
