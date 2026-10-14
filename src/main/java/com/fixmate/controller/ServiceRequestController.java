package com.fixmate.controller;

import com.fixmate.dto.RequestStatusRequest;
import com.fixmate.dto.ProviderMatchResponse;
import com.fixmate.dto.ServiceRequestRequest;
import com.fixmate.dto.ServiceRequestResponse;
import com.fixmate.entity.RequestStatus;
import com.fixmate.entity.UserRole;
import com.fixmate.service.ProviderMatchingService;
import com.fixmate.service.ServiceRequestService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Validated
@RestController
@RequestMapping("/api/requests")
public class ServiceRequestController {

    private final ServiceRequestService requestService;
    private final ProviderMatchingService providerMatchingService;

    public ServiceRequestController(
            ServiceRequestService requestService,
            ProviderMatchingService providerMatchingService
    ) {
        this.requestService = requestService;
        this.providerMatchingService = providerMatchingService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ServiceRequestResponse createRequest(
            @AuthenticationPrincipal UserDetails currentUser,
            @Valid @RequestBody ServiceRequestRequest request,
            @org.springframework.web.bind.annotation.RequestHeader(value = "Idempotency-Key", required = false) String clientKey
    ) {
        return requestService.createRequest(currentUser.getUsername(), request, clientKey);
    }

    @GetMapping("/my")
    public List<ServiceRequestResponse> getMyRequests(@AuthenticationPrincipal UserDetails currentUser) {
        return requestService.getMyRequests(currentUser.getUsername());
    }

    @PostMapping("/{id}/book")
    public ServiceRequestResponse book(@PathVariable @Positive Long id,
            @AuthenticationPrincipal UserDetails user,
            @Valid @RequestBody com.fixmate.dto.ProviderBookingRequest request) {
        return requestService.bookProvider(id, user.getUsername(), request.providerId());
    }

    @GetMapping("/relevant")
    public List<ServiceRequestResponse> getRelevantRequests(
            @AuthenticationPrincipal UserDetails currentUser,
            @RequestParam(defaultValue = "50") @DecimalMin("0.1") @DecimalMax("500") double radiusKm
    ) {
        return requestService.getRelevantRequests(currentUser.getUsername(), radiusKm);
    }

        @GetMapping("/provider/requests/accepted")
        public List<ServiceRequestResponse> getProviderAcceptedRequests(
            @AuthenticationPrincipal UserDetails currentUser
        ) {
        return requestService.getProviderRequestsByStatuses(
            currentUser.getUsername(),
            java.util.Set.of(RequestStatus.ACCEPTED, RequestStatus.IN_PROGRESS)
        );
        }

        @GetMapping("/provider/requests/completed")
        public List<ServiceRequestResponse> getProviderCompletedRequests(
            @AuthenticationPrincipal UserDetails currentUser
        ) {
        return requestService.getProviderRequestsByStatuses(
            currentUser.getUsername(),
            java.util.Set.of(RequestStatus.COMPLETED)
        );
        }

    @GetMapping("/{id}/matches")
    public List<ProviderMatchResponse> getMatches(
            @PathVariable @Positive Long id,
            @AuthenticationPrincipal UserDetails currentUser,
            @RequestParam(defaultValue = "5") @Min(1) @Max(20) int limit
    ) {
        return providerMatchingService.findMatches(
                id,
                currentUser.getUsername(),
                getRole(currentUser),
                limit
        );
    }

    @GetMapping
    public List<ServiceRequestResponse> getAllRequests() {
        return requestService.getAllRequests();
    }

    @GetMapping("/{id}")
    public ServiceRequestResponse getRequest(
            @PathVariable @Positive Long id,
            @AuthenticationPrincipal UserDetails currentUser
    ) {
        return requestService.getRequest(id, currentUser.getUsername(), getRole(currentUser));
    }

    @PutMapping("/{id}")
    public ServiceRequestResponse updateRequest(
            @PathVariable @Positive Long id,
            @AuthenticationPrincipal UserDetails currentUser,
            @Valid @RequestBody ServiceRequestRequest request
    ) {
        return requestService.updateRequest(id, currentUser.getUsername(), request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void cancelRequest(
            @PathVariable @Positive Long id,
            @AuthenticationPrincipal UserDetails currentUser
    ) {
        requestService.cancelRequest(id, currentUser.getUsername());
    }

    @PutMapping("/{id}/status")
    public ServiceRequestResponse updateStatus(
            @PathVariable @Positive Long id,
            @AuthenticationPrincipal UserDetails currentUser,
            @Valid @RequestBody RequestStatusRequest request
    ) {
        return requestService.updateStatus(
                id, currentUser.getUsername(), getRole(currentUser), request.status());
    }

    private UserRole getRole(UserDetails userDetails) {
        return userDetails.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(authority -> authority.startsWith("ROLE_"))
                .map(authority -> UserRole.valueOf(authority.substring("ROLE_".length())))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Authenticated user has no recognized role"));
    }
}
