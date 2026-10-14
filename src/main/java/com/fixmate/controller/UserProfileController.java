package com.fixmate.controller;
import com.fixmate.dto.UserProfileRequest;
import com.fixmate.dto.AdminUserResponse;
import com.fixmate.service.UserProfileService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/users/me")
public class UserProfileController {
    private final UserProfileService service;
    public UserProfileController(UserProfileService service) { this.service = service; }
    @GetMapping
    public AdminUserResponse get(@AuthenticationPrincipal UserDetails user) { return service.get(user.getUsername()); }
    @PutMapping
    public AdminUserResponse update(@AuthenticationPrincipal UserDetails user, @Valid @RequestBody UserProfileRequest request) {
        return service.update(user.getUsername(), request);
    }
}
