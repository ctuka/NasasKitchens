package com.nanaskitchens.api.payments;

import com.stripe.StripeClient;
import com.stripe.exception.StripeException;
import com.stripe.model.PaymentIntent;
import com.stripe.net.RequestOptions;
import com.stripe.param.PaymentIntentCreateParams;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/**
 * Story 3.4 — real PaymentIntents (NFR6: card data never touches our servers; the client
 * confirms with Stripe Elements/PaymentSheet against the clientSecret). The architecture's
 * Connect transfer_data/application_fee is deferred: sellers have no Connect onboarding
 * story yet, so charges land on the platform account and payout math uses the
 * commissionCents already recorded on each Order (FR21).
 */
@Component
@ConditionalOnProperty(name = "app.payments.provider", havingValue = "stripe")
public class StripePaymentProvider implements PaymentProvider {

    private final StripeClient client;
    private final String publishableKey;

    public StripePaymentProvider(
            @Value("${app.payments.stripe.secret-key}") String secretKey,
            @Value("${app.payments.stripe.publishable-key}") String publishableKey) {
        if (secretKey == null || secretKey.isBlank()) {
            throw new IllegalStateException("app.payments.provider=stripe requires STRIPE_SECRET_KEY");
        }
        this.client = new StripeClient(secretKey);
        this.publishableKey = publishableKey;
    }

    @Override
    public String name() {
        return "stripe";
    }

    @Override
    public Intent createIntent(String orderId, int amountCents, String idempotencyKey) {
        PaymentIntentCreateParams params = PaymentIntentCreateParams.builder()
                .setAmount((long) amountCents)
                .setCurrency("usd")
                .putMetadata("orderId", orderId)
                .setAutomaticPaymentMethods(
                        PaymentIntentCreateParams.AutomaticPaymentMethods.builder().setEnabled(true).build())
                .build();
        RequestOptions options = RequestOptions.builder().setIdempotencyKey(idempotencyKey).build();
        try {
            PaymentIntent intent = client.paymentIntents().create(params, options);
            return new Intent(intent.getId(), intent.getClientSecret(), intent.getStatus());
        } catch (StripeException e) {
            // Thrown inside the order transaction → rollback restores the decremented portions.
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "PAYMENT_PROVIDER_ERROR");
        }
    }

    @Override
    public boolean tryCancelIntent(String paymentIntentId) {
        try {
            client.paymentIntents().cancel(paymentIntentId);
            return true;
        } catch (StripeException e) {
            return false; // terminal state (e.g. already succeeded) — let the webhook settle it
        }
    }

    @Override
    public String publishableKey() {
        return publishableKey;
    }
}
