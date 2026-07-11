package com.nanaskitchens.api.inventory;

import java.util.List;

/** Published after a transaction that changed portion counts; fans out to SSE subscribers. */
public record PortionsChanged(List<String> menuItemIds) {
}
