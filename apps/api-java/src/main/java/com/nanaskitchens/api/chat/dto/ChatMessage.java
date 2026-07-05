package com.nanaskitchens.api.chat.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record ChatMessage(@Pattern(regexp = "user|assistant") String role, @NotBlank String content) {
}
