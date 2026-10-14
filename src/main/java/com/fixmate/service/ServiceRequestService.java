package com.fixmate.service;

import com.fixmate.client.GeminiClient;
import com.fixmate.dto.AIAnalysisResult;
import com.fixmate.dto.AIAnalysisResponse;
import com.fixmate.dto.ServiceRequestRequest;
import com.fixmate.dto.ServiceRequestResponse;
import com.fixmate.entity.AIAnalysis;
import com.fixmate.entity.NotificationType;
import com.fixmate.entity.ProviderAvailability;
import com.fixmate.entity.RequestPriority;
import com.fixmate.entity.RequestStatus;
import com.fixmate.entity.ServiceCategory;
import com.fixmate.entity.ServiceProvider;
import com.fixmate.entity.ServiceRequest;
import com.fixmate.entity.User;
import com.fixmate.entity.UserRole;
import com.fixmate.exception.ResourceNotFoundException;
import com.fixmate.repository.AIAnalysisRepository;
import com.fixmate.repository.ServiceCategoryRepository;
import com.fixmate.repository.ServiceProviderRepository;
import com.fixmate.repository.ServiceRequestRepository;
import com.fixmate.repository.UserRepository;
import com.fixmate.util.LocationDistanceCalculator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Set;

@Service
public class ServiceRequestService {

    private static final Logger LOGGER = LoggerFactory.getLogger(ServiceRequestService.class);

    private static final Set<RequestStatus> OPEN_STATUSES = Set.of(
            RequestStatus.PENDING,
            RequestStatus.AI_ANALYZED,
            RequestStatus.MATCHING,
            RequestStatus.PROVIDER_SELECTED
    );

    private final ServiceRequestRepository requestRepository;
    private final ServiceCategoryRepository categoryRepository;
    private final ServiceProviderRepository providerRepository;
    private final UserRepository userRepository;
    private final AIAnalysisRepository aiAnalysisRepository;
    private final GeminiClient aiServiceClient;
    private final NotificationService notificationService;

    public ServiceRequestService(
            ServiceRequestRepository requestRepository,
            ServiceCategoryRepository categoryRepository,
            ServiceProviderRepository providerRepository,
            UserRepository userRepository,
            AIAnalysisRepository aiAnalysisRepository,
            GeminiClient aiServiceClient,
            NotificationService notificationService
    ) {
        this.requestRepository = requestRepository;
        this.categoryRepository = categoryRepository;
        this.providerRepository = providerRepository;
        this.userRepository = userRepository;
        this.aiAnalysisRepository = aiAnalysisRepository;
        this.aiServiceClient = aiServiceClient;
        this.notificationService = notificationService;
    }

    @Transactional
    public ServiceRequestResponse createRequest(String userEmail, ServiceRequestRequest request, String clientKey) {
        if (clientKey != null && !clientKey.matches("[A-Za-z0-9_-]{1,64}")) throw new IllegalArgumentException("Invalid request retry key");
        User user = userRepository.findByEmailForUpdate(userEmail)
                .orElseThrow(() -> new ResourceNotFoundException("User account not found"));

        if (user.getRole() != UserRole.USER) {
            throw new AccessDeniedException("Only user accounts can create service requests");
        }

        ServiceCategory category = findCategory(request.categoryId());

        if (clientKey != null) {
            var existing = requestRepository.findByUser_IdAndClientRequestKey(user.getId(), clientKey);
            if (existing.isPresent()) return toResponse(existing.get());
        }

        ServiceRequest serviceRequest = new ServiceRequest(
                user,
                category,
                request.title().trim(),
                request.description().trim(),
                request.address().trim(),
                request.latitude(),
                request.longitude(),
                request.preferredDate(),
                request.preferredTime(),
                request.priority()
        );

        serviceRequest.setContactNumber(request.contactNumber().trim());
        serviceRequest.setClientRequestKey(clientKey);

        serviceRequest = requestRepository.save(serviceRequest);

        notificationService.notifyRequestOwner(
                serviceRequest,
                NotificationType.NEW_SERVICE_REQUEST,
                "Service request received",
                "Your service request has been created."
        );

        try {
            AIAnalysisResponse analysis = aiServiceClient.analyze(
                    serviceRequest.getDescription(),
                    serviceRequest.getCategory().getName(),
                    categoryRepository.findAllByOrderByNameAsc().stream().map(ServiceCategory::getName).toList()
            );

            RequestPriority recommendedPriority =
                    RequestPriority.valueOf(analysis.priority().toUpperCase());

            AIAnalysis savedAnalysis = aiAnalysisRepository.save(new AIAnalysis(
                    serviceRequest,
                    analysis.predictedCategory(),
                    analysis.possibleProblem(),
                    analysis.troubleshootingSteps(),
                    analysis.recommendedService(),
                    analysis.safetyWarning(),
                    recommendedPriority.name(),
                    java.math.BigDecimal.valueOf(analysis.confidence()).setScale(
                            4,
                            java.math.RoundingMode.HALF_UP
                    )
            ));

            savedAnalysis.setDecision(analysis.canSelfFix(), analysis.technicianRequired(), analysis.possibleCauses());
            categoryRepository.findAllByOrderByNameAsc().stream().filter(c -> c.getName().equals(analysis.predictedCategory())).findFirst().ifPresent(serviceRequest::changeCategory);
            serviceRequest.applyAIAnalysis(savedAnalysis.getId(), recommendedPriority);
            requestRepository.save(serviceRequest);

            notificationService.notifyRequestOwner(
                    serviceRequest,
                    NotificationType.AI_ANALYSIS_COMPLETED,
                    "AI analysis completed",
                    "Your request has been analyzed and categorized as "
                            + analysis.predictedCategory() + "."
            );

        } catch (GeminiClient.AIServiceUnavailableException exception) {
            LOGGER.warn(
                    "AI analysis unavailable for service request {}: {}",
                    serviceRequest.getId(),
                    exception.getMessage()
            );
        }

        return toResponse(serviceRequest);
    }

    @Transactional(readOnly = true)
    public ServiceRequestResponse getRequest(Long id, String userEmail, UserRole role) {
        ServiceRequest request = findRequest(id);

        if (role == UserRole.ADMIN
                || request.getUser().getEmail().equalsIgnoreCase(userEmail)) {
            return toResponse(request);
        }

        if (role == UserRole.PROVIDER && canProviderView(request, userEmail)) {
            return toResponse(request);
        }

        throw new AccessDeniedException("You are not allowed to view this request");
    }

    @Transactional(readOnly = true)
    public List<ServiceRequestResponse> getMyRequests(String userEmail) {
        User user = findUser(userEmail);

        return requestRepository
                .findAllByUser_IdOrderByCreatedAtDesc(user.getId())
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ServiceRequestResponse> getAllRequests() {
        return requestRepository
                .findAllByOrderByCreatedAtDesc()
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ServiceRequestResponse> getRelevantRequests(
            String userEmail,
            double radiusKm
    ) {
        ServiceProvider provider = findProviderForUser(userEmail);
        return requestRepository.findAllByAssignedProvider_IdAndStatusInOrderByUpdatedAtDesc(
                provider.getId(), Set.of(RequestStatus.REQUESTED)).stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<ServiceRequestResponse> getProviderRequestsByStatuses(
            String userEmail,
            Set<RequestStatus> statuses
    ) {
        ServiceProvider provider = findProviderForUser(userEmail);

        return requestRepository
                .findAllByAssignedProvider_IdAndStatusInOrderByUpdatedAtDesc(
                        provider.getId(),
                        statuses
                )
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public ServiceRequestResponse updateRequest(
            Long id,
            String userEmail,
            ServiceRequestRequest update
    ) {
        ServiceRequest request = findRequestForUpdate(id);

        requireOwner(request, userEmail);

        if (request.getStatus() != RequestStatus.PENDING
                && request.getStatus() != RequestStatus.AI_ANALYZED) {
            throw new IllegalArgumentException(
                    "Only pending or AI-analyzed requests can be edited"
            );
        }

        request.changeCategory(findCategory(update.categoryId()));
        request.clearAIAnalysis();

        request.updateDetails(
                update.title().trim(),
                update.description().trim(),
                update.address().trim(),
                update.latitude(),
                update.longitude(),
                update.preferredDate(),
                update.preferredTime(),
                update.priority()
        );

        request.setContactNumber(update.contactNumber().trim());

        return toResponse(request);
    }

    @Transactional
    public void cancelRequest(Long id, String userEmail) {
        ServiceRequest request = findRequestForUpdate(id);

        requireOwner(request, userEmail);

        if (request.getStatus() == RequestStatus.COMPLETED
                || request.getStatus() == RequestStatus.CANCELLED
                || request.getStatus() == RequestStatus.IN_PROGRESS) {
            throw new IllegalArgumentException(
                    "This request can no longer be cancelled"
            );
        }

        request.changeStatus(RequestStatus.CANCELLED);
        notifyStatusChange(request, RequestStatus.CANCELLED);
    }

    @Transactional
    public ServiceRequestResponse updateStatus(
            Long id,
            String userEmail,
            UserRole role,
            RequestStatus newStatus
    ) {
        ServiceRequest request = findRequestForUpdate(id);

        if (role == UserRole.USER) {
            requireOwner(request, userEmail);

            if (newStatus != RequestStatus.CANCELLED) {
                throw new AccessDeniedException(
                        "Users may only cancel their own requests"
                );
            }

            cancelStatus(request);

        } else if (role == UserRole.PROVIDER) {
            updateProviderStatus(request, userEmail, newStatus);

        } else {
            updateAdminStatus(request, newStatus);
        }

        notifyStatusChange(request, newStatus);

        return toResponse(request);
    }

    private void notifyStatusChange(
            ServiceRequest request,
            RequestStatus status
    ) {
        switch (status) {
            case REJECTED -> notificationService.notifyRequestOwner(request, NotificationType.PROVIDER_REJECTED_REQUEST,
                    "Booking declined", "The selected provider declined. You can choose another technician.");
            case ACCEPTED -> notificationService.notifyRequestOwner(
                    request,
                    NotificationType.PROVIDER_ACCEPTED_REQUEST,
                    "Provider accepted your request",
                    "A service provider has accepted your request."
            );

            case IN_PROGRESS -> notificationService.notifyRequestOwner(
                    request,
                    NotificationType.SERVICE_STARTED,
                    "Service started",
                    "Work on your service request has started."
            );

            case COMPLETED -> notificationService.notifyRequestOwner(
                    request,
                    NotificationType.SERVICE_COMPLETED,
                    "Service completed",
                    "Your service request has been marked completed."
            );

            case CANCELLED -> {
                notificationService.notifyRequestOwner(
                        request,
                        NotificationType.REQUEST_CANCELLED,
                        "Request cancelled",
                        "Your service request has been cancelled."
                );

                notificationService.notifyAssignedProvider(
                        request,
                        NotificationType.REQUEST_CANCELLED,
                        "Request cancelled",
                        "A service request assigned to you has been cancelled."
                );
            }

            default -> {
            }
        }
    }

    private void updateProviderStatus(ServiceRequest request, String userEmail, RequestStatus newStatus) {
        ServiceProvider provider = findProviderForUser(userEmail);
        requireAssignedProvider(request, provider);
        if (request.getStatus() == RequestStatus.REQUESTED && newStatus == RequestStatus.REJECTED) {
            request.changeStatus(RequestStatus.REJECTED);
            return;
        }
        if (request.getStatus() == RequestStatus.REQUESTED && newStatus == RequestStatus.ACCEPTED
                && provider.getAvailabilityStatus() == ProviderAvailability.AVAILABLE) {
            request.changeStatus(RequestStatus.ACCEPTED);
            return;
        }
        if (request.getStatus() == RequestStatus.ACCEPTED && newStatus == RequestStatus.IN_PROGRESS) {
            request.changeStatus(RequestStatus.IN_PROGRESS);
            return;
        }
        if (request.getStatus() == RequestStatus.IN_PROGRESS && newStatus == RequestStatus.COMPLETED) {
            request.changeStatus(RequestStatus.COMPLETED);
            provider.recordCompletedJob();
            return;
        }
        throw new IllegalArgumentException("Invalid provider status transition");
    }

    @Transactional
    public ServiceRequestResponse bookProvider(Long id, String userEmail, Long providerId) {
        ServiceRequest request = findRequestForUpdate(id);
        requireOwner(request, userEmail);
        if (!Set.of(RequestStatus.PENDING, RequestStatus.AI_ANALYZED, RequestStatus.MATCHING,
                RequestStatus.PROVIDER_SELECTED, RequestStatus.REJECTED).contains(request.getStatus()))
            throw new IllegalArgumentException("This request cannot be booked in its current status");
        ServiceProvider provider = providerRepository.findWithProfileById(providerId)
                .orElseThrow(() -> new ResourceNotFoundException("Provider not found"));
        if (!provider.getUser().isActive() || provider.getAvailabilityStatus() != ProviderAvailability.AVAILABLE
                || !isRelevantToProvider(request, provider)) throw new IllegalArgumentException("This provider is not available for this category and service area");
        request.bookProvider(provider);
        notificationService.notifyAssignedProvider(request, NotificationType.REQUEST_BOOKED,
                "New repair booking", "A customer has requested your repair service. Accept or reject the booking.");
        notificationService.notifyRequestOwner(request, NotificationType.REQUEST_BOOKED,
                "Booking sent", "Your booking was sent to " + provider.getUser().getFullName() + ".");
        return toResponse(request);
    }

    private void updateAdminStatus(
            ServiceRequest request,
            RequestStatus newStatus
    ) {
        if (!isAllowedTransition(request.getStatus(), newStatus)) {
            throw new IllegalArgumentException(
                    "Invalid request status transition"
            );
        }

        if ((newStatus == RequestStatus.ACCEPTED
                || newStatus == RequestStatus.IN_PROGRESS
                || newStatus == RequestStatus.COMPLETED)
                && request.getAssignedProvider() == null) {
            throw new IllegalArgumentException(
                    "A provider must be assigned before accepting or progressing work"
            );
        }

        if (newStatus == RequestStatus.COMPLETED
                && request.getAssignedProvider() != null) {
            request.getAssignedProvider().recordCompletedJob();
        }

        request.changeStatus(newStatus);
    }

    private void cancelStatus(ServiceRequest request) {
        if (request.getStatus() == RequestStatus.COMPLETED
                || request.getStatus() == RequestStatus.CANCELLED
                || request.getStatus() == RequestStatus.IN_PROGRESS) {
            throw new IllegalArgumentException(
                    "This request can no longer be cancelled"
            );
        }

        request.changeStatus(RequestStatus.CANCELLED);
    }

    private boolean canProviderView(ServiceRequest request, String userEmail) {
        ServiceProvider provider = findProviderForUser(userEmail);
        return request.getAssignedProvider() != null && request.getAssignedProvider().getId().equals(provider.getId());
    }

    private boolean isRelevantToProvider(
            ServiceRequest request,
            ServiceProvider provider
    ) {
        boolean matchingCategory = provider.getServiceCategories()
                .stream()
                .anyMatch(category ->
                        category.getId()
                                .equals(request.getCategory().getId()));

        return matchingCategory
                && LocationDistanceCalculator.calculateKm(
                        provider.getLatitude(),
                        provider.getLongitude(),
                        request.getLatitude(),
                        request.getLongitude()
                ) <= provider.getServiceAreaKm();
    }

    private void requireAssignedProvider(
            ServiceRequest request,
            ServiceProvider provider
    ) {
        if (request.getAssignedProvider() == null
                || !request.getAssignedProvider()
                .getId()
                .equals(provider.getId())) {
            throw new AccessDeniedException(
                    "Only the assigned provider may update this request"
            );
        }
    }

    private boolean isAllowedTransition(
            RequestStatus current,
            RequestStatus target
    ) {
        return switch (current) {
            case REQUESTED -> Set.of(RequestStatus.ACCEPTED, RequestStatus.REJECTED, RequestStatus.CANCELLED).contains(target);
            case REJECTED -> Set.of(RequestStatus.REQUESTED, RequestStatus.CANCELLED).contains(target);
            case PENDING -> Set.of(
                    RequestStatus.AI_ANALYZED,
                    RequestStatus.MATCHING,
                    RequestStatus.PROVIDER_SELECTED,
                    RequestStatus.ACCEPTED,
                    RequestStatus.CANCELLED
            ).contains(target);

            case AI_ANALYZED -> Set.of(
                    RequestStatus.MATCHING,
                    RequestStatus.PROVIDER_SELECTED,
                    RequestStatus.ACCEPTED,
                    RequestStatus.CANCELLED
            ).contains(target);

            case MATCHING -> Set.of(
                    RequestStatus.PROVIDER_SELECTED,
                    RequestStatus.ACCEPTED,
                    RequestStatus.CANCELLED
            ).contains(target);

            case PROVIDER_SELECTED -> Set.of(
                    RequestStatus.ACCEPTED,
                    RequestStatus.CANCELLED
            ).contains(target);

            case ACCEPTED -> Set.of(
                    RequestStatus.IN_PROGRESS,
                    RequestStatus.CANCELLED
            ).contains(target);

            case IN_PROGRESS -> Set.of(
                    RequestStatus.COMPLETED,
                    RequestStatus.CANCELLED
            ).contains(target);

            case COMPLETED, CANCELLED -> false;
        };
    }

    private ServiceRequest findRequest(Long id) {
        return requestRepository
                .findWithDetailsById(id)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Service request not found: " + id
                        ));
    }

    private ServiceRequest findRequestForUpdate(Long id) {
        return requestRepository
                .findByIdForUpdate(id)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Service request not found: " + id
                        ));
    }

    private User findUser(String email) {
        return userRepository
                .findByEmail(email)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "User account not found"
                        ));
    }

    private ServiceCategory findCategory(Long id) {
        return categoryRepository
                .findById(id)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Service category not found: " + id
                        ));
    }

    private ServiceProvider findProviderForUser(String email) {
        User user = findUser(email);

        return providerRepository
                .findByUser_Id(user.getId())
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Provider profile not found"
                        ));
    }

    private void requireOwner(
            ServiceRequest request,
            String userEmail
    ) {
        if (!request.getUser()
                .getEmail()
                .equalsIgnoreCase(userEmail)) {
            throw new AccessDeniedException(
                    "You can only manage your own requests"
            );
        }
    }

    private ServiceRequestResponse toResponse(
            ServiceRequest request
    ) {
        AIAnalysisResult analysisResult =
                request.getAiAnalysisId() == null
                        ? null
                        : aiAnalysisRepository
                        .findByIdAndServiceRequest_Id(
                                request.getAiAnalysisId(),
                                request.getId()
                        )
                        .map(this::toAnalysisResult)
                        .orElse(null);

        return new ServiceRequestResponse(
                request.getId(),
                request.getUser().getId(),
                request.getCategory().getId(),
                request.getCategory().getName(),
                request.getTitle(),
                request.getDescription(),
                request.getAddress(),
                request.getContactNumber(),
                request.getLocation(),
                request.getLatitude(),
                request.getLongitude(),
                request.getPreferredDate(),
                request.getPreferredTime(),
                request.getPriority(),
                request.getStatus(),
                request.getAiAnalysisId(),
                analysisResult,
                request.getAssignedProvider() == null
                        ? null
                        : request.getAssignedProvider().getId(),
                request.getCreatedAt(),
                request.getUpdatedAt()
        );
    }

    private AIAnalysisResult toAnalysisResult(
            AIAnalysis analysis
    ) {
        return new AIAnalysisResult(
                analysis.getId(),
                analysis.getPredictedCategory(),
                analysis.getPossibleProblem(),
                analysis.getTroubleshootingSteps(),
                analysis.getRecommendedService(),
                analysis.getSafetyWarning(),
                analysis.getPriority(),
                analysis.getConfidence(),
                analysis.getCreatedAt(),
                analysis.getImageAnalysisStatus(), analysis.getCanSelfFix(), analysis.getTechnicianRequired(), analysis.getPossibleCauses(), analysis.getSource()
        );
    }

    @Transactional
    public void analyzeUploadedImage(ServiceRequest request, org.springframework.core.io.Resource image, String contentType) {
        if (request.getStatus() != RequestStatus.PENDING && request.getStatus() != RequestStatus.AI_ANALYZED) return;
        try {
            var result = aiServiceClient.analyzeImage(request.getDescription(), request.getCategory().getName(), categoryRepository.findAllByOrderByNameAsc().stream().map(ServiceCategory::getName).toList(), image, contentType);
            var analysis = new AIAnalysis(request, result.predictedCategory(), result.possibleProblem(), result.troubleshootingSteps(),
                    result.recommendedService(), result.safetyWarning(), result.priority().toUpperCase(),
                    java.math.BigDecimal.valueOf(result.confidence()).setScale(4, java.math.RoundingMode.HALF_UP));
            analysis.setImageAnalysisStatus(result.imageAnalysisStatus());
            analysis.setDecision(result.canSelfFix(), result.technicianRequired(), result.possibleCauses());
            analysis = aiAnalysisRepository.save(analysis);
            categoryRepository.findAllByOrderByNameAsc().stream().filter(c -> c.getName().equals(result.predictedCategory())).findFirst().ifPresent(request::changeCategory);
            request.applyAIAnalysis(analysis.getId(), RequestPriority.valueOf(result.priority().toUpperCase()));
        } catch (GeminiClient.AIServiceUnavailableException exception) {
            if (request.getAiAnalysisId() != null) aiAnalysisRepository.findById(request.getAiAnalysisId())
                    .ifPresent(analysis -> analysis.setImageAnalysisStatus("UNAVAILABLE"));
            LOGGER.warn("Image analysis unavailable for request {}; preserving saved request and text analysis", request.getId());
        }
    }
}
