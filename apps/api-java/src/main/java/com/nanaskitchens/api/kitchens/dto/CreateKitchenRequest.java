package com.nanaskitchens.api.kitchens.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateKitchenRequest(
        @NotBlank @Size(min = 2) String name,
        @NotBlank String cuisineTag,
        @NotNull String description,
        @NotBlank String address,
        @NotNull Double lat,
        @NotNull Double lng) {
}
