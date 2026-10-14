package com.fixmate.service;
import com.fixmate.dto.AdminUserResponse;
import com.fixmate.dto.UserProfileRequest;
import com.fixmate.entity.User;
import com.fixmate.repository.UserRepository;
import com.fixmate.exception.ResourceNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserProfileService {
    private final UserRepository users;
    public UserProfileService(UserRepository users) { this.users = users; }
    @Transactional(readOnly = true)
    public AdminUserResponse get(String email) { return response(find(email)); }
    @Transactional
    public AdminUserResponse update(String email, UserProfileRequest request) {
        var user = find(email);
        user.updateContactDetails(request.name().trim(), request.phone() == null ? null : request.phone().trim());
        return response(users.save(user));
    }
    private User find(String email) { return users.findByEmail(email).orElseThrow(() -> new ResourceNotFoundException("User not found")); }
    public static AdminUserResponse response(User user) {
        return new AdminUserResponse(user.getId(), user.getFullName(), user.getEmail(), user.getPhone(), user.getRole(), user.isActive(), user.getCreatedAt());
    }
}
