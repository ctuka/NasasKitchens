package com.nanaskitchens.api.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @Email @NotBlank String email,
        @NotBlank @Size(min = 8) String password,
        // Only self-serve roles; inspector/admin are assigned by an admin (mirrors AC2).
        @Pattern(regexp = "buyer|seller") String role) {
}
