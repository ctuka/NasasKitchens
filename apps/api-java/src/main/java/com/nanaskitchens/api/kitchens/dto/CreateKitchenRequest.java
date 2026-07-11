package com.nanaskitchens.api.kitchens.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Story 1.3: the address is geocoded server-side; lat/lng are only the manual
 * fallback the UI offers after a GEOCODING_FAILED response (AC4). */
public record CreateKitchenRequest(
        @NotBlank @Size(min = 2) String name,
        @NotBlank String cuisineTag,
        @NotNull String description,
        @NotBlank String address,
        Double lat,
        Double lng) {
}
