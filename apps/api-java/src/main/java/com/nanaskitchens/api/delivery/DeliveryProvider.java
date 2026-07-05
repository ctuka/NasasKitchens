package com.nanaskitchens.api.delivery;

/**
 * Story 4.2/4.3 — delivery is abstracted behind this interface (architecture.md). DoorDash
 * Drive and Grubhub implementations plug in here; until partner credentials exist the mock
 * implementation ships behind the same interface (documented mitigation for partner-API
 * access delays).
 */
public interface DeliveryProvider {

    /** Prisma "DeliveryProvider" enum value: doordash | grubhub | mock. */
    String name();

    Quote quote(String pickupAddress, String orderId);

    CreatedDelivery create(String quoteId, String orderId);

    record Quote(String quoteId, int feeCents) {
    }

    record CreatedDelivery(String externalId, String trackingUrl, int feeCents) {
    }
}
