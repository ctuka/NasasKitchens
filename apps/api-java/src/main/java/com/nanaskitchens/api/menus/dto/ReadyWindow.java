package com.nanaskitchens.api.menus.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/** Story 2.2 — a pickup/delivery ready-time window, e.g. {"start":"17:00","end":"20:00","slotMinutes":30}. */
public record ReadyWindow(
        @NotBlank @Pattern(regexp = "([01]\\d|2[0-3]):[0-5]\\d") String start,
        @NotBlank @Pattern(regexp = "([01]\\d|2[0-3]):[0-5]\\d") String end,
        @Min(5) @Max(240) int slotMinutes) {

    public int startMinutes() {
        return toMinutes(start);
    }

    public int endMinutes() {
        return toMinutes(end);
    }

    private static int toMinutes(String hhmm) {
        return Integer.parseInt(hhmm.substring(0, 2)) * 60 + Integer.parseInt(hhmm.substring(3));
    }
}
