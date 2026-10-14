package com.fixmate.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record AIAnalysisResponse(
        @JsonProperty("predictedCategory") String predictedCategory,
        @JsonProperty("possibleProblem") String possibleProblem,
        @JsonProperty("troubleshootingSteps") List<String> troubleshootingSteps,
        @JsonProperty("recommendedService") String recommendedService,
        @JsonProperty("safetyWarning") String safetyWarning,
        String priority,
        Double confidence,
        String imageAnalysisStatus,
        Boolean canSelfFix,
        Boolean technicianRequired,
        List<String> possibleCauses
) {
}
