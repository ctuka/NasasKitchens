package com.nanaskitchens.api.delivery;

import java.util.Map;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

/**
 * Story 4.2 AC3 — delivery partner webhooks. Public endpoint: authentication is the
 * HMAC-SHA256 signature over the raw body (X-Delivery-Signature, hex).
 */
@RestController
public class DeliveryWebhookController {

    private final DeliveryService delivery;

    public DeliveryWebhookController(DeliveryService delivery) {
        this.delivery = delivery;
    }

    @PostMapping("/webhooks/delivery/{provider}")
    public Map<String, Object> webhook(
            @PathVariable String provider,
            @RequestBody byte[] rawBody,
            @RequestHeader(value = "X-Delivery-Signature", required = false) String signature) {
        return delivery.processWebhook(provider, rawBody, signature);
    }
}
