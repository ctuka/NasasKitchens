package com.nanaskitchens.api.menus.dto;

import jakarta.validation.Valid;
import java.util.List;

/** Draft-only updates: either field may be omitted to leave it unchanged; items is a full replace. */
public record UpdateMenuDayRequest(
        @Valid List<ReadyWindow> readyWindows,
        @Valid List<CreateMenuDayRequest.Item> items) {
}
