package com.fixmate.dto;

import java.time.Instant;

public record RequestImageResponse(
        Long id,
        String originalFilename,
        String contentType,
        long fileSizeBytes,
        String url,
        Instant uploadedAt
) {
}