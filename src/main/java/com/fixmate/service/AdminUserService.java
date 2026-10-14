package com.fixmate.service;

import com.fixmate.dto.AdminUserResponse;
import com.fixmate.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class AdminUserService {

    private final UserRepository userRepository;

    @Transactional
    public AdminUserResponse setActive(Long id, boolean active) {
        var user = userRepository.findById(id).orElseThrow(() -> new com.fixmate.exception.ResourceNotFoundException("User not found"));
        if (user.getRole() == com.fixmate.entity.UserRole.ADMIN) throw new IllegalArgumentException("Admin accounts cannot be disabled here");
        user.setActive(active);
        return UserProfileService.response(userRepository.save(user));
    }

    public AdminUserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public List<AdminUserResponse> getAllUsers() {
        return userRepository.findAll()
                .stream()
                .map(user -> new AdminUserResponse(
                        user.getId(),
                        user.getFullName(),
                        user.getEmail(),
                        user.getPhone(),
                        user.getRole(),
                        user.isActive(),
                        user.getCreatedAt()
                ))
                .toList();
    }
}
