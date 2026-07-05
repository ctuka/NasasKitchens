package com.nanaskitchens.api.kitchens.dto;

import java.time.LocalDateTime;
import java.util.List;

/** Public profile — the street address NEVER leaves this serializer (FR10 / Story 1.3 AC3). */
public record KitchenProfile(
        String id,
        String name,
        String cuisineTag,
        String description,
        List<String> photos,
        Double ratingAvg,
        int ratingCount,
        Integer hygieneScoreTotal,
        LocalDateTime hygieneScoredAt,
        LocalDateTime complianceAttestedAt) {
}
