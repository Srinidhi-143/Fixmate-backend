package com.fixmate.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record AIAnalysisResult(
        Long id,
        String predictedCategory,
        String possibleProblem,
        List<String> troubleshootingSteps,
        String recommendedService,
        String safetyWarning,
        String priority,
        BigDecimal confidence,
        Instant analyzedAt,
        String imageAnalysisStatus,
        Boolean canSelfFix,
        Boolean technicianRequired,
        List<String> possibleCauses,
        String source
) {
}
