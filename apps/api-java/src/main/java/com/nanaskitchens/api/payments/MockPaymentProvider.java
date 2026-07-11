package com.nanaskitchens.api.payments;

import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Instant-success stand-in used until Stripe keys are provisioned: orders confirm
 * immediately (the pre-3.4 behaviour), so the web checkout and the chat agent keep
 * working with zero configuration.
 */
@Component
@ConditionalOnProperty(name = "app.payments.provider", havingValue = "mock", matchIfMissing = true)
public class MockPaymentProvider implements PaymentProvider {

    @Override
    public String name() {
        return "mock";
    }

    @Override
    public Intent createIntent(String orderId, int amountCents, String idempotencyKey) {
        return new Intent("mockpi_" + UUID.randomUUID(), null, "succeeded");
    }

    @Override
    public boolean tryCancelIntent(String paymentIntentId) {
        return true;
    }

    @Override
    public String refund(String paymentIntentId, int amountCents) {
        return "mockre_" + UUID.randomUUID();
    }

    @Override
    public String publishableKey() {
        return null;
    }
}
