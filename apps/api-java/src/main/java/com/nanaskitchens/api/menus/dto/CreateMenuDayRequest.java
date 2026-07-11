package com.nanaskitchens.api.menus.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

/** Story 2.1 — a dated draft menu with its dishes and portion counts. */
public record CreateMenuDayRequest(
        @NotBlank String date,
        @NotEmpty @Valid List<ReadyWindow> readyWindows,
        @Valid List<Item> items) {

    public record Item(@NotBlank String dishId, @Min(1) int portionsTotal) {
    }
}
