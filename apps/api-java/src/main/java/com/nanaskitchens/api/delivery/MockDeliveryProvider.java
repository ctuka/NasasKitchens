package com.nanaskitchens.api.delivery;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Sandbox stand-in used until DoorDash/Grubhub credentials are provisioned. Deterministic
 * fee, fake tracking URL; webhooks are simulated by calling POST /webhooks/delivery/mock
 * with the shared-secret HMAC signature.
 */
@Component
@ConditionalOnProperty(name = "app.delivery.provider", havingValue = "mock", matchIfMissing = true)
public class MockDeliveryProvider implements DeliveryProvider {

    private static final int BASE_FEE_CENTS = 399;

    private final Map<String, Integer> quotes = new ConcurrentHashMap<>();

    @Override
    public String name() {
        return "mock";
    }

    @Override
    public Quote quote(String pickupAddress, String orderId) {
        // Deterministic pseudo-distance fee so tests are stable: base + 0..300 by order hash.
        int feeCents = BASE_FEE_CENTS + Math.floorMod(orderId.hashCode(), 4) * 100;
        String quoteId = "mockq_" + UUID.randomUUID();
        quotes.put(quoteId, feeCents);
        return new Quote(quoteId, feeCents);
    }

    @Override
    public CreatedDelivery create(String quoteId, String orderId) {
        Integer feeCents = quotes.remove(quoteId);
        if (feeCents == null) {
            feeCents = BASE_FEE_CENTS;
        }
        String externalId = "mockd_" + UUID.randomUUID();
        return new CreatedDelivery(externalId, "https://track.example.com/mock/" + externalId, feeCents);
    }
}
