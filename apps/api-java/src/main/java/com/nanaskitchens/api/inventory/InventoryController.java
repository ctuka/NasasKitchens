package com.nanaskitchens.api.inventory;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/inventory")
public class InventoryController {

    public record RemainingRequest(@NotNull List<String> menuItemIds) {
    }

    private final InventoryService inventory;

    public InventoryController(InventoryService inventory) {
        this.inventory = inventory;
    }

    @PostMapping("/remaining")
    public List<Map<String, Object>> remaining(@Valid @RequestBody RemainingRequest request) {
        return inventory.remaining(request.menuItemIds());
    }
}
