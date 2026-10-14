package com.fixmate.controller;

import com.fixmate.dto.ProviderProfileRequest;
import com.fixmate.dto.ProviderProfileResponse;
import com.fixmate.service.ServiceProviderService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Positive;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.validation.annotation.Validated;

import java.util.List;

@Validated
@RestController
@RequestMapping("/api/providers")
public class ServiceProviderController {

    private final ServiceProviderService providerService;

    public ServiceProviderController(ServiceProviderService providerService) {
        this.providerService = providerService;
    }

    @PostMapping("/profile")
    @ResponseStatus(HttpStatus.CREATED)
    public ProviderProfileResponse createProfile(
            @AuthenticationPrincipal UserDetails currentUser,
            @Valid @RequestBody ProviderProfileRequest request
    ) {
        return providerService.createProfile(currentUser.getUsername(), request);
    }

    @GetMapping("/profile")
    public ProviderProfileResponse getCurrentUserProfile(
            @AuthenticationPrincipal UserDetails currentUser
    ) {
        return providerService.getCurrentUserProfile(currentUser.getUsername());
    }

    @GetMapping("/{id}")
    public ProviderProfileResponse getProvider(@PathVariable @Positive Long id) {
        return providerService.getProvider(id);
    }

    @PutMapping("/{id}")
    public ProviderProfileResponse updateProfile(
            @PathVariable @Positive Long id,
            @AuthenticationPrincipal UserDetails currentUser,
            @Valid @RequestBody ProviderProfileRequest request
    ) {
        boolean admin = currentUser.getAuthorities().stream()
                .anyMatch(authority -> authority.getAuthority().equals("ROLE_ADMIN"));
        return providerService.updateProfile(id, currentUser.getUsername(), admin, request);
    }

    @GetMapping
    public List<ProviderProfileResponse> getProviders() {
        return providerService.getProviders();
    }

    @GetMapping("/category/{categoryId}")
    public List<ProviderProfileResponse> getProvidersByCategory(
            @PathVariable @Positive Long categoryId
    ) {
        return providerService.getProvidersByCategory(categoryId);
    }

    @GetMapping("/nearby")
    public List<ProviderProfileResponse> getNearbyProviders(
            @RequestParam @DecimalMin("-90.0") @DecimalMax("90.0") double latitude,
            @RequestParam @DecimalMin("-180.0") @DecimalMax("180.0") double longitude,
            @RequestParam(defaultValue = "25") @DecimalMin("0.1") @DecimalMax("500") double radiusKm
    ) {
        return providerService.getNearbyProviders(latitude, longitude, radiusKm);
    }
}