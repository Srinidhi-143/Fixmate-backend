package com.fixmate.service;

import com.fixmate.dto.ProviderMatchResponse;
import com.fixmate.entity.ProviderAvailability;
import com.fixmate.entity.NotificationType;
import com.fixmate.entity.ServiceCategory;
import com.fixmate.entity.ServiceProvider;
import com.fixmate.entity.ServiceRequest;
import com.fixmate.entity.UserRole;
import com.fixmate.exception.ResourceNotFoundException;
import com.fixmate.repository.ServiceProviderRepository;
import com.fixmate.repository.ServiceRequestRepository;
import com.fixmate.util.LocationDistanceCalculator;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class ProviderMatchingService {

    private static final Set<String> GENERIC_SKILL_WORDS = Set.of(
            "and", "the", "for", "with", "repair", "service", "services", "technician"
    );
    private static final Pattern WORD_SEPARATOR = Pattern.compile("[^a-z0-9]+");

    private final ServiceRequestRepository requestRepository;
    private final ServiceProviderRepository providerRepository;
        private final NotificationService notificationService;

    public ProviderMatchingService(
            ServiceRequestRepository requestRepository,
            ServiceProviderRepository providerRepository,
            NotificationService notificationService
    ) {
        this.requestRepository = requestRepository;
        this.providerRepository = providerRepository;
                this.notificationService = notificationService;
    }

        @Transactional
    public List<ProviderMatchResponse> findMatches(
            Long requestId,
            String requesterEmail,
            UserRole requesterRole,
            int limit
    ) {
        ServiceRequest request = requestRepository.findWithDetailsById(requestId)
                .orElseThrow(() -> new ResourceNotFoundException("Service request not found: " + requestId));

        boolean isOwner = request.getUser().getEmail().equalsIgnoreCase(requesterEmail);
        if (requesterRole != UserRole.ADMIN && !isOwner) {
            throw new AccessDeniedException("Only the request owner or an admin can view provider matches");
        }

        List<ProviderMatchResponse> matches = providerRepository.findAllByOrderByIdAsc().stream()
                .filter(provider -> provider.getAvailabilityStatus() == ProviderAvailability.AVAILABLE)
                .filter(provider -> provider.getUser().isActive())
                .filter(provider -> hasCategory(provider, request.getCategory().getId()))
                .filter(provider -> distanceKm(provider, request) <= provider.getServiceAreaKm())
                .map(provider -> toMatch(provider, request))
                .sorted(Comparator.comparingDouble(ProviderMatchResponse::matchingScore)
                        .reversed()
                        .thenComparingDouble(ProviderMatchResponse::distance)
                        .thenComparing(ProviderMatchResponse::rating, Comparator.reverseOrder())
                        .thenComparing(ProviderMatchResponse::providerId))
                .limit(limit)
                .toList();
        if (!matches.isEmpty()) {
            notificationService.notifyRequestOwner(
                    request,
                    NotificationType.PROVIDER_MATCHED,
                    "Providers matched",
                    matches.size() + " suitable service provider(s) match your request."
            );
        }
        return matches;
    }

    private ProviderMatchResponse toMatch(ServiceProvider provider, ServiceRequest request) {
        double distance = distanceKm(provider, request);
        double score = matchingScore(provider, request, distance);
        return new ProviderMatchResponse(
                provider.getId(),
                provider.getUser().getFullName(),
                provider.getSkills().stream().sorted().toList(),
                provider.getExperienceYears(),
                provider.getRating(),
                round(distance),
                provider.getAvailabilityStatus(),
                round(score)
        );
    }

    private double matchingScore(ServiceProvider provider, ServiceRequest request, double distanceKm) {
        boolean categoryMatch = hasCategory(provider, request.getCategory().getId());
        double categoryScore = categoryMatch ? 1.0 : 0.0;

        double skillScore = skillMatch(provider, request);
        double locationScore = Math.max(0.0, 1.0 - distanceKm / provider.getServiceAreaKm());

        double ratingScore = provider.getRating()
                .max(BigDecimal.ZERO)
                .min(BigDecimal.valueOf(5))
                .doubleValue() / 5.0;
        double completedJobsScore = Math.min(provider.getCompletedJobs(), 10) / 10.0;
        double qualityScore = ratingScore * 0.8 + completedJobsScore * 0.2;

        double availabilityScore = switch (provider.getAvailabilityStatus()) {
            case AVAILABLE -> 1.0;
            case BUSY -> 0.5;
            case UNAVAILABLE -> 0.0;
        };

        return categoryScore * 40
                + skillScore * 20
                + locationScore * 20
                + qualityScore * 10
                + availabilityScore * 10;
    }

    private double skillMatch(ServiceProvider provider, ServiceRequest request) {
        Set<String> requestWords = words(
                request.getTitle() + " " + request.getDescription() + " " + request.getCategory().getName()
        );
        Set<String> skillWords = provider.getSkills().stream()
                .flatMap(skill -> words(skill).stream())
                .collect(Collectors.toCollection(LinkedHashSet::new));

        long overlappingWords = skillWords.stream().filter(requestWords::contains).count();
        return Math.min(1.0, overlappingWords / 2.0);
    }

    private Set<String> words(String value) {
        return WORD_SEPARATOR.splitAsStream(value.toLowerCase(Locale.ROOT))
                .filter(word -> word.length() > 2)
                .filter(word -> !GENERIC_SKILL_WORDS.contains(word))
                .collect(Collectors.toSet());
    }

    private boolean hasCategory(ServiceProvider provider, Long categoryId) {
        return provider.getServiceCategories().stream()
                .map(ServiceCategory::getId)
                .anyMatch(categoryId::equals);
    }

        private double distanceKm(ServiceProvider provider, ServiceRequest request) {
                return LocationDistanceCalculator.calculateKm(
                                request.getLatitude(), request.getLongitude(),
                                provider.getLatitude(), provider.getLongitude()
                );
    }

    private double round(double value) {
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }
}