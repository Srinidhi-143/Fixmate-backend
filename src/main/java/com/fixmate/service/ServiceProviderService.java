package com.fixmate.service;

import com.fixmate.dto.ProviderCategoryResponse;
import com.fixmate.dto.ProviderProfileRequest;
import com.fixmate.dto.ProviderProfileResponse;
import com.fixmate.entity.ServiceCategory;
import com.fixmate.entity.ServiceProvider;
import com.fixmate.entity.User;
import com.fixmate.entity.UserRole;
import com.fixmate.exception.ResourceNotFoundException;
import com.fixmate.repository.ServiceCategoryRepository;
import com.fixmate.repository.ServiceProviderRepository;
import com.fixmate.repository.UserRepository;
import com.fixmate.util.LocationDistanceCalculator;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class ServiceProviderService {

    private final ServiceProviderRepository providerRepository;
    private final UserRepository userRepository;
    private final ServiceCategoryRepository categoryRepository;

    public ServiceProviderService(
            ServiceProviderRepository providerRepository,
            UserRepository userRepository,
            ServiceCategoryRepository categoryRepository
    ) {
        this.providerRepository = providerRepository;
        this.userRepository = userRepository;
        this.categoryRepository = categoryRepository;
    }

    @Transactional
    public ProviderProfileResponse createProfile(String userEmail, ProviderProfileRequest request) {
        User user = findUser(userEmail);
        if (user.getRole() != UserRole.PROVIDER) {
            throw new AccessDeniedException("Only provider accounts can create a provider profile");
        }
        if (providerRepository.existsByUser_Id(user.getId())) {
            throw new IllegalArgumentException("This account already has a provider profile");
        }

        ServiceProvider provider = new ServiceProvider(
                user,
                cleanSkills(request.skills()),
                findCategories(request.serviceCategoryIds()),
                request.experienceYears(),
                request.location().trim(),
                request.latitude(),
                request.longitude(),
                request.serviceAreaKm(),
                request.availabilityStatus(),
                cleanDescription(request.description())
        );
        user.updateContactDetails(request.name().trim(), request.phone().trim());
        return toResponse(providerRepository.save(provider));
    }

    @Transactional(readOnly = true)
    public ProviderProfileResponse getProvider(Long id) {
        return toResponse(findProvider(id));
    }

    @Transactional(readOnly = true)
    public ProviderProfileResponse getCurrentUserProfile(String userEmail) {
        User user = findUser(userEmail);
        if (user.getRole() != UserRole.PROVIDER) {
            throw new AccessDeniedException("Only provider accounts can view a provider profile");
        }
        ServiceProvider provider = providerRepository.findByUser_Id(user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Provider profile not found"));
        return toResponse(provider);
    }

    @Transactional(readOnly = true)
    public List<ProviderProfileResponse> getProviders() {
        return providerRepository.findAllByOrderByIdAsc().stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ProviderProfileResponse> getProvidersByCategory(Long categoryId) {
        if (!categoryRepository.existsById(categoryId)) {
            throw new ResourceNotFoundException("Category not found: " + categoryId);
        }
        return providerRepository.findDistinctByServiceCategories_IdOrderByIdAsc(categoryId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ProviderProfileResponse> getNearbyProviders(double latitude, double longitude, double radiusKm) {
        return providerRepository.findAllByOrderByIdAsc().stream()
                .filter(provider -> LocationDistanceCalculator.calculateKm(latitude, longitude,
                        provider.getLatitude(), provider.getLongitude()) <= radiusKm)
                .sorted(Comparator.comparingDouble(provider -> LocationDistanceCalculator.calculateKm(latitude, longitude,
                        provider.getLatitude(), provider.getLongitude())))
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public ProviderProfileResponse updateProfile(
            Long id,
            String userEmail,
            boolean admin,
            ProviderProfileRequest request
    ) {
        ServiceProvider provider = findProvider(id);
        if (!admin && !provider.getUser().getEmail().equalsIgnoreCase(userEmail)) {
            throw new AccessDeniedException("You can only update your own provider profile");
        }

        provider.getUser().updateContactDetails(request.name().trim(), request.phone().trim());
        provider.updateProfile(
                cleanSkills(request.skills()),
                findCategories(request.serviceCategoryIds()),
                request.experienceYears(),
                request.location().trim(),
                request.latitude(),
                request.longitude(),
                request.serviceAreaKm(),
                request.availabilityStatus(),
                cleanDescription(request.description())
        );
        return toResponse(provider);
    }

    private User findUser(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User account not found"));
    }

    private ServiceProvider findProvider(Long id) {
        return providerRepository.findWithProfileById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Provider not found: " + id));
    }

    private Set<ServiceCategory> findCategories(List<Long> categoryIds) {
        Set<Long> uniqueIds = new LinkedHashSet<>(categoryIds);
        List<ServiceCategory> categories = categoryRepository.findAllById(uniqueIds);
        if (categories.size() != uniqueIds.size()) {
            throw new IllegalArgumentException("One or more service categories do not exist");
        }
        return new LinkedHashSet<>(categories);
    }

    private Set<String> cleanSkills(List<String> skills) {
        return skills.stream()
                .map(String::trim)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private String cleanDescription(String description) {
        return description == null || description.isBlank() ? null : description.trim();
    }

    private ProviderProfileResponse toResponse(ServiceProvider provider) {
        List<ProviderCategoryResponse> categories = provider.getServiceCategories().stream()
                .sorted(Comparator.comparing(ServiceCategory::getName))
                .map(category -> new ProviderCategoryResponse(
                        category.getId(), category.getName(), category.getSlug()))
                .toList();

        return new ProviderProfileResponse(
                provider.getId(),
                provider.getUser().getId(),
                provider.getUser().getFullName(),
                provider.getUser().getPhone(),
                Set.copyOf(provider.getSkills()),
                provider.getExperienceYears(),
                categories,
                provider.getLocation(),
                provider.getLatitude(),
                provider.getLongitude(),
                provider.getServiceAreaKm(),
                provider.getAvailabilityStatus(),
                provider.getDescription(),
                provider.getRating(),
                provider.getCompletedJobs(),
                provider.getCreatedAt(),
                provider.getUpdatedAt()
        );
    }

}