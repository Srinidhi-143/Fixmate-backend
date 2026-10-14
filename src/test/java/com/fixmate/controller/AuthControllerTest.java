package com.fixmate.controller;
import com.fixmate.service.*;
import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;
class AuthControllerTest {
    @Test void recoveryReturnsChallengeAndVerifiedResetToken() {
        var service = mock(PasswordResetService.class);
        when(service.requestReset("demo@example.test")).thenReturn("challenge");
        when(service.verifyOtp("challenge", "123456")).thenReturn("reset");
        var controller = new AuthController(mock(AuthService.class), service);
        assertEquals("challenge", controller.requestPasswordReset(new PasswordResetRequest("demo@example.test")).get("challengeToken"));
        assertEquals("reset", controller.verifyOtp(new DemoOtpRequest("challenge", "123456")).get("resetToken"));
    }
}
