package com.nanaskitchens.api.kitchens.dto;

import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * Story 1.3 — PATCH /kitchens/{id}: every field optional; omitted fields keep their
 * value. photos is a full replace (remove/reorder); uploads go through POST /photos.
 * A new address is re-encrypted and re-geocoded (lat/lng are the manual fallback, AC4).
 */
public record UpdateKitchenRequest(
        @Size(min = 2) String name,
        String cuisineTag,
        String description,
        String address,
        Double lat,
        Double lng,
        @Size(max = 10) List<String> photos) {
}
