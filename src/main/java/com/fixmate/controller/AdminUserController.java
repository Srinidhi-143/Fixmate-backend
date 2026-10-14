package com.fixmate.controller;

import com.fixmate.dto.AdminUserResponse;
import com.fixmate.service.AdminUserService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/admin/users")
public class AdminUserController {

    private final AdminUserService adminUserService;

    public AdminUserController(AdminUserService adminUserService) {
        this.adminUserService = adminUserService;
    }

    @GetMapping
    public List<AdminUserResponse> getAllUsers() {
        return adminUserService.getAllUsers();
    }

    public record ActiveRequest(@jakarta.validation.constraints.NotNull Boolean active) {}

    @org.springframework.web.bind.annotation.PutMapping("/{id}/active")
    public AdminUserResponse setActive(@org.springframework.web.bind.annotation.PathVariable Long id,
            @jakarta.validation.Valid @org.springframework.web.bind.annotation.RequestBody ActiveRequest request) {
        return adminUserService.setActive(id, request.active());
    }
}
