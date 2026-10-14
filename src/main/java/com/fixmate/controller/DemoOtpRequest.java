package com.fixmate.controller;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
public record DemoOtpRequest(@NotBlank @Size(max = 128) String challengeToken,
                             @NotBlank @Pattern(regexp = "[0-9]{6}") String otp) {}
