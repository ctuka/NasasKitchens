package com.nanaskitchens.api.inventory;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.Map;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/inventory")
public class InventoryController {

    public record RemainingRequest(@NotNull List<String> menuItemIds) {
    }

    public record AdjustRequest(@NotNull Integer delta) {
    }

    private final InventoryService inventory;

    public InventoryController(InventoryService inventory) {
        this.inventory = inventory;
    }

    @PostMapping("/remaining")
    public List<Map<String, Object>> remaining(@Valid @RequestBody RemainingRequest request) {
        return inventory.remaining(request.menuItemIds());
    }

    /** Story 2.3 AC4 — seller manual portion correction. */
    @PostMapping("/menu-items/{menuItemId}/adjust")
    @PreAuthorize("hasRole('SELLER')")
    public Map<String, Object> adjust(
            Authentication auth, @PathVariable String menuItemId, @Valid @RequestBody AdjustRequest request) {
        return inventory.adjust(auth.getName(), menuItemId, request.delta());
    }
}
