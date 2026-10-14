package com.fixmate.controller;

import com.fixmate.dto.AuthResponse;
import com.fixmate.dto.LoginRequest;
import com.fixmate.dto.RegisterRequest;
import com.fixmate.service.AuthService;
import com.fixmate.service.PasswordResetService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final PasswordResetService passwordResetService;

    public AuthController(AuthService authService, PasswordResetService passwordResetService) {
        this.authService = authService;
        this.passwordResetService = passwordResetService;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public AuthResponse register(@Valid @RequestBody RegisterRequest request) {
        return authService.register(request);
    }

    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }

    @PostMapping("/password-reset/request")
    public Map<String, String> requestPasswordReset(@Valid @RequestBody PasswordResetRequest request) {
        return Map.of("challengeToken", passwordResetService.requestReset(request.email()),
                "message", "Enter the demo OTP to continue");
    }

    @PostMapping("/password-reset/verify-otp")
    public Map<String, String> verifyOtp(@Valid @RequestBody DemoOtpRequest request) {
        return Map.of("resetToken", passwordResetService.verifyOtp(request.challengeToken(), request.otp()));
    }

    @PostMapping("/password-reset/confirm")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void confirmPasswordReset(@Valid @RequestBody PasswordResetConfirmRequest request) {
        passwordResetService.resetPassword(request.token(), request.newPassword());
    }
}
