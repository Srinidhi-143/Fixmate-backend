package com.fixmate.service;

import com.fixmate.entity.*;
import com.fixmate.exception.InvalidPasswordResetTokenException;
import com.fixmate.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import java.time.Instant;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PasswordResetServiceTest {
    @Mock UserRepository users;
    @Mock PasswordResetTokenRepository tokens;
    @Mock PasswordEncoder encoder;
    PasswordResetService service;
    User user;
    @BeforeEach void setup() {
        service = new PasswordResetService(users, tokens, encoder);
        user = new User("Demo", "demo@example.test", "old-hash", UserRole.USER);
        ReflectionTestUtils.setField(user, "id", 2L);
    }
    PasswordResetToken token() { return new PasswordResetToken(user, "initial-hash", Instant.now().plusSeconds(900)); }
    void found(PasswordResetToken record) { when(tokens.findByTokenHashForUpdate(anyString())).thenReturn(Optional.of(record)); }

    @Test void unknownEmailFails() {
        when(users.findByEmailForUpdate("none@example.test")).thenReturn(Optional.empty());
        assertThrows(IllegalArgumentException.class, () -> service.requestReset("none@example.test"));
        verifyNoInteractions(tokens, encoder);
    }
    @Test void requestStoresHashRatherThanChallenge() {
        when(users.findByEmailForUpdate(user.getEmail())).thenReturn(Optional.of(user));
        when(tokens.findByUserIdForUpdate(2L)).thenReturn(Optional.empty());
        String challenge = service.requestReset(" Demo@Example.test ");
        var captor = org.mockito.ArgumentCaptor.forClass(PasswordResetToken.class);
        verify(tokens).save(captor.capture());
        assertNotEquals(challenge, captor.getValue().getTokenHash());
        assertEquals(64, captor.getValue().getTokenHash().length());
        assertFalse(captor.getValue().isOtpVerified());
    }
    @Test void wrongOtpFails() {
        var record = token(); found(record);
        assertThrows(IllegalArgumentException.class, () -> service.verifyOtp("challenge", "000000"));
        assertFalse(record.isOtpVerified()); verify(tokens, never()).save(any());
    }
    @Test void correctOtpRotatesTokenAndVerifiesStage() {
        var record = token(); found(record);
        String reset = service.verifyOtp("challenge", "123456");
        assertTrue(record.isOtpVerified()); assertNotEquals("initial-hash", record.getTokenHash());
        assertNotEquals(reset, record.getTokenHash()); verify(tokens).save(record);
    }
    @Test void cannotSkipOtp() {
        found(token());
        assertThrows(InvalidPasswordResetTokenException.class, () -> service.resetPassword("challenge", "new-password"));
        verifyNoInteractions(encoder);
    }
    @Test void verifiedTokenHashesPasswordAndIsConsumed() {
        var record = token(); record.verifyOtp(); found(record);
        when(encoder.encode("new-password")).thenReturn("bcrypt-hash");
        service.resetPassword("verified", "new-password");
        assertEquals("bcrypt-hash", user.getPasswordHash()); assertNotNull(record.getUsedAt());
        verify(users).save(user);
    }
    @Test void expiredTokenFails() {
        found(new PasswordResetToken(user, "hash", Instant.now().minusSeconds(1)));
        assertThrows(InvalidPasswordResetTokenException.class, () -> service.verifyOtp("expired", "123456"));
    }
    @Test void reusedTokenFails() {
        var record = token(); record.verifyOtp(); record.markUsed(Instant.now()); found(record);
        assertThrows(InvalidPasswordResetTokenException.class, () -> service.resetPassword("used", "new-password"));
    }
    @Test void disabledAccountCannotRecover() {
        user.setActive(false);
        when(users.findByEmailForUpdate(user.getEmail())).thenReturn(Optional.of(user));
        assertThrows(IllegalArgumentException.class, () -> service.requestReset(user.getEmail()));
    }
    @Test void invalidTokenFails() {
        when(tokens.findByTokenHashForUpdate(anyString())).thenReturn(Optional.empty());
        assertThrows(InvalidPasswordResetTokenException.class, () -> service.verifyOtp("invalid", "123456"));
    }
}
