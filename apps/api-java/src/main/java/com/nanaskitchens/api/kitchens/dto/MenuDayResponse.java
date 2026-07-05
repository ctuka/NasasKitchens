package com.nanaskitchens.api.kitchens.dto;

import java.time.LocalDate;
import java.util.List;
import tools.jackson.databind.JsonNode;

/** Published daily menu, same nested shape the NestJS endpoint returned. */
public record MenuDayResponse(
        String id,
        String kitchenId,
        LocalDate date,
        String status,
        JsonNode readyWindows,
        List<Item> items) {

    public record Item(
            String id,
            String menuDayId,
            String dishId,
            int portionsTotal,
            int portionsRemaining,
            Dish dish) {
    }

    public record Dish(
            String id,
            String kitchenId,
            String name,
            String description,
            String photo,
            int priceCents,
            List<String> dietaryTags) {
    }
}
