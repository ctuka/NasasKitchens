package com.nanaskitchens.api.kitchens.dto;

/** Mirrors KitchenSearchResult in packages/core (NFR3: one shape across REST/MCP/agent). */
public record KitchenSearchResult(
        String id,
        String name,
        String cuisineTag,
        double distanceMiles,
        Double ratingAvg,
        Integer hygieneScore,
        int portionsLeftToday) {
}
