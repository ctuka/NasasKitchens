package com.nanaskitchens.api.menus.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;

/** Create payload; for PATCH the same shape is used with all fields optional. */
public record DishRequest(
        @NotBlank String name,
        @NotNull String description,
        String photo,
        @NotNull @Min(1) Integer priceCents,
        List<String> dietaryTags) {

    public record Patch(String name, String description, String photo, @Min(1) Integer priceCents,
            List<String> dietaryTags) {
    }
}
