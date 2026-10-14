package com.fixmate.service;
import com.fixmate.dto.UserProfileRequest;
import com.fixmate.entity.*;
import com.fixmate.repository.UserRepository;
import org.junit.jupiter.api.Test;
import java.util.Optional;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;
class UserProfileServiceTest {
    @Test void profileUpdatesOnlyNameAndPhone() {
        var users = mock(UserRepository.class);
        var user = new User("Before", "owner@example.test", "hash", UserRole.USER);
        when(users.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
        when(users.save(user)).thenReturn(user);
        var result = new UserProfileService(users).update(user.getEmail(), new UserProfileRequest(" After ", "9999999999"));
        assertEquals("After", result.name()); assertEquals("9999999999", result.phone());
        assertEquals("owner@example.test", result.email()); assertEquals(UserRole.USER, result.role());
    }
    @Test void adminCannotDisableAdminAccounts() {
        var users = mock(UserRepository.class);
        var admin = new User("Admin", "admin@example.test", "hash", UserRole.ADMIN);
        when(users.findById(1L)).thenReturn(Optional.of(admin));
        assertThrows(IllegalArgumentException.class, () -> new AdminUserService(users).setActive(1L, false));
        assertTrue(admin.isActive()); verify(users, never()).save(any());
    }
    @Test void profileDtoRejectsBlankNameAndInvalidPhone() {
        try (var factory = jakarta.validation.Validation.buildDefaultValidatorFactory()) {
            assertEquals(2, factory.getValidator().validate(new UserProfileRequest(" ", "invalid")).size());
        }
    }
}
