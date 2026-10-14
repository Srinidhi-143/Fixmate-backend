package com.fixmate.entity;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "ai_analysis", indexes = {
        @Index(name = "idx_ai_analysis_request", columnList = "request_id")
})
public class AIAnalysis {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "request_id", nullable = false)
    private ServiceRequest serviceRequest;

    @Column(name = "predicted_category", nullable = false, length = 100)
    private String predictedCategory;

    @Column(name = "possible_problem", nullable = false, columnDefinition = "TEXT")
    private String possibleProblem;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "ai_analysis_troubleshooting_steps",
            joinColumns = @JoinColumn(name = "analysis_id"))
    @OrderColumn(name = "step_order")
    @Column(name = "step", nullable = false, columnDefinition = "TEXT")
    private List<String> troubleshootingSteps = new ArrayList<>();

    @Column(name = "recommended_service", nullable = false, length = 100)
    private String recommendedService;

    @Column(name = "safety_warning", columnDefinition = "TEXT")
    private String safetyWarning;

    @Column(nullable = false, length = 20)
    private String priority;

    @Column(nullable = false, precision = 5, scale = 4)
    private BigDecimal confidence;

    @Column(name = "can_self_fix")
    private Boolean canSelfFix;
    @Column(name = "technician_required")
    private Boolean technicianRequired;
    @Column(nullable = false, length = 20)
    private String source = "LEGACY";
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "ai_analysis_possible_causes", joinColumns = @JoinColumn(name = "analysis_id"))
    @OrderColumn(name = "cause_order")
    @Column(name = "cause", nullable = false, columnDefinition = "TEXT")
    private List<String> possibleCauses = new ArrayList<>();
    public Boolean getCanSelfFix() { return canSelfFix; }
    public Boolean getTechnicianRequired() { return technicianRequired; }
    public String getSource() { return source; }
    public List<String> getPossibleCauses() { return possibleCauses; }
    public void setDecision(Boolean selfFix, Boolean technician, List<String> causes) {
        this.canSelfFix = selfFix; this.technicianRequired = technician;
        this.possibleCauses = new ArrayList<>(causes); this.source = "GEMINI";
    }

    @Column(name = "image_analysis_status", nullable = false, length = 30)
    private String imageAnalysisStatus = "NOT_PROVIDED";
    public String getImageAnalysisStatus() { return imageAnalysisStatus; }
    public void setImageAnalysisStatus(String status) {
        imageAnalysisStatus = java.util.Set.of("ANALYZED", "UNAVAILABLE", "NOT_PROVIDED").contains(status == null ? "" : status) ? status : "UNAVAILABLE";
    }

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    protected AIAnalysis() {
    }

    public AIAnalysis(ServiceRequest serviceRequest, String predictedCategory, String possibleProblem,
                      List<String> troubleshootingSteps, String recommendedService,
                      String safetyWarning, String priority, BigDecimal confidence) {
        this.serviceRequest = serviceRequest;
        this.predictedCategory = predictedCategory;
        this.possibleProblem = possibleProblem;
        this.troubleshootingSteps.addAll(troubleshootingSteps);
        this.recommendedService = recommendedService;
        this.safetyWarning = safetyWarning;
        this.priority = priority;
        this.confidence = confidence;
    }

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public Long getRequestId() {
        return serviceRequest.getId();
    }

    public String getPredictedCategory() {
        return predictedCategory;
    }

    public String getPossibleProblem() {
        return possibleProblem;
    }

    public List<String> getTroubleshootingSteps() {
        return troubleshootingSteps;
    }

    public String getRecommendedService() {
        return recommendedService;
    }

    public String getSafetyWarning() {
        return safetyWarning;
    }

    public String getPriority() {
        return priority;
    }

    public BigDecimal getConfidence() {
        return confidence;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
