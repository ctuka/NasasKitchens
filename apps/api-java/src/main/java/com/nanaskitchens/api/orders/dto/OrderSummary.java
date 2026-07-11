package com.nanaskitchens.api.orders.dto;

import java.util.List;

/** FR15 priced summary — returned as-is when confirm=false, and written to the audit log. */
public record OrderSummary(
        String kitchenId,
        List<Item> items,
        String readySlot,
        String fulfillment,
        String deliveryAddress,
        int foodSubtotalCents,
        int deliveryFeeCents,
        int courierTipCents,
        int totalCents) {

    public record Item(String menuItemId, String dish, int qty, int unitPriceCents) {
    }
}
