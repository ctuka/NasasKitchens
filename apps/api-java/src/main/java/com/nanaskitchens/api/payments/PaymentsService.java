package com.nanaskitchens.api.payments;

import com.stripe.Stripe;
import com.stripe.exception.StripeException;
import com.stripe.model.PaymentIntent;
import com.stripe.net.RequestOptions;
import com.stripe.param.PaymentIntentCreateParams;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/**
 * Story 3.4 (first slice): charge the buyer when an order is confirmed.
 *
 * Dev/test mode: confirms the PaymentIntent server-side with Stripe's shared test payment
 * method, so no card UI is needed yet. Production will move confirmation to the client
 * (PaymentSheet) and add Connect transfers to sellers; this service keeps that seam.
 *
 * With no STRIPE_SECRET_KEY configured the service is disabled and orders confirm unpaid,
 * matching the pre-Stripe behaviour.
 */
@Service
public class PaymentsService {

    private final String secretKey;

    public PaymentsService(@Value("${app.stripe.secret-key:}") String secretKey) {
        this.secretKey = secretKey;
        if (!secretKey.isBlank()) {
            Stripe.apiKey = secretKey;
        }
    }

    public boolean enabled() {
        return !secretKey.isBlank();
    }

    /**
     * Creates and confirms a PaymentIntent for the order total. Returns the PaymentIntent id,
     * or null when Stripe is not configured. The order's idempotencyKey doubles as the Stripe
     * idempotency key (architecture: "id reused as Stripe idempotency key").
     */
    public String charge(int amountCents, String idempotencyKey, String orderId) {
        if (!enabled()) {
            return null;
        }
        try {
            PaymentIntentCreateParams params = PaymentIntentCreateParams.builder()
                    .setAmount((long) amountCents)
                    .setCurrency("usd")
                    .addPaymentMethodType("card")
                    .setPaymentMethod("pm_card_visa") // Stripe test-mode payment method
                    .setConfirm(true)
                    .putMetadata("orderId", orderId)
                    .build();
            RequestOptions options = RequestOptions.builder().setIdempotencyKey(idempotencyKey).build();
            PaymentIntent intent = PaymentIntent.create(params, options);
            if (!"succeeded".equals(intent.getStatus())) {
                throw new ResponseStatusException(
                        HttpStatus.PAYMENT_REQUIRED, "PAYMENT_FAILED:" + intent.getStatus());
            }
            return intent.getId();
        } catch (StripeException e) {
            // Thrown inside the order transaction: order + inventory decrement roll back together.
            throw new ResponseStatusException(HttpStatus.PAYMENT_REQUIRED, "PAYMENT_FAILED:" + e.getCode());
        }
    }
}
