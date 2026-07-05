package com.nanaskitchens.api.chat;

import com.nanaskitchens.api.inventory.InventoryService;
import com.nanaskitchens.api.kitchens.KitchensService;
import com.nanaskitchens.api.orders.OrdersService;
import com.nanaskitchens.api.orders.dto.CreateOrderRequest;
import java.util.List;
import java.util.Map;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.json.JsonMapper;

/**
 * Agent tools (NFR3: same capabilities as the MCP server), all backed by this service's own
 * domain services. Errors are returned as JSON strings so the model can recover (e.g. offer
 * alternatives on PORTIONS_CONFLICT) instead of the stream aborting.
 */
public class KitchenOrderTools {

    private final KitchensService kitchens;
    private final OrdersService orders;
    private final InventoryService inventory;
    private final JsonMapper jsonMapper;
    private final String buyerId;

    KitchenOrderTools(
            KitchensService kitchens,
            OrdersService orders,
            InventoryService inventory,
            JsonMapper jsonMapper,
            String buyerId) {
        this.kitchens = kitchens;
        this.orders = orders;
        this.inventory = inventory;
        this.jsonMapper = jsonMapper;
        this.buyerId = buyerId;
    }

    @Tool(description = "Search for kitchens within 10 miles. Returns list with name, cuisine, distance, portions left.")
    public String searchKitchens(double lat, double lng, @ToolParam(required = false) String cuisine) {
        return guarded(() -> kitchens.search(lat, lng, cuisine));
    }

    @Tool(description = "Get the published daily menu for a kitchen.")
    public String getMenu(
            String kitchenId,
            @ToolParam(required = false, description = "ISO date string, defaults to today") String date) {
        return guarded(() -> kitchens.publishedMenu(kitchenId, date));
    }

    @Tool(description = "Check live remaining portions for a list of menu item IDs.")
    public String checkPortions(List<String> menuItemIds) {
        return guarded(() -> inventory.remaining(menuItemIds));
    }

    @Tool(
            description =
                    "Create an order. With confirm=false returns a priced summary only (FR15). "
                            + "Call with confirm=false first, show summary to user, then call with confirm=true "
                            + "after explicit confirmation.")
    public String createOrder(CreateOrderRequest input) {
        return guarded(() -> orders.place(buyerId, input));
    }

    @Tool(description = "Get the status of an existing order.")
    public String getOrderStatus(String orderId) {
        return guarded(() -> orders.detail(buyerId, orderId));
    }

    @Tool(description = "Cancel an order. Inventory is automatically restored.")
    public String cancelOrder(String orderId) {
        return guarded(() -> orders.cancel(buyerId, orderId));
    }

    private String guarded(java.util.function.Supplier<Object> call) {
        try {
            Object result = call.get();
            return result == null ? "null" : jsonMapper.writeValueAsString(result);
        } catch (ResponseStatusException e) {
            return jsonMapper.writeValueAsString(Map.of(
                    "error", e.getReason() != null ? e.getReason() : "ERROR",
                    "status", e.getStatusCode().value()));
        }
    }
}
