package com.fixmate.dto;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
public record UserProfileRequest(@NotBlank @Size(max = 150) String name,
                                 @Pattern(regexp = "\\+?[0-9() -]{7,32}|^$") String phone) {}
