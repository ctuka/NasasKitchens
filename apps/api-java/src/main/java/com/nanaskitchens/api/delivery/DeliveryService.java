package com.nanaskitchens.api.delivery;

import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

/**
 * Story 4.2 (dev slice): hands confirmed delivery orders to a courier provider.
 *
 * Only the mock provider is implemented; it creates a DeliveryJob row with a fake courier
 * and tracking URL so the agent/UI flow completes end-to-end. The real DoorDash Drive
 * provider (quotes, webhooks, retries) plugs in behind this same entry point once Drive
 * developer credentials exist.
 */
@Service
public class DeliveryService {

    public record Job(String provider, String status, String trackingUrl, int feeCents) {
    }

    private final JdbcClient db;
    private final String provider;
    private final int mockFeeCents;

    public DeliveryService(
            JdbcClient db,
            @Value("${app.delivery.provider:mock}") String provider,
            @Value("${app.delivery.mock-fee-cents:399}") int mockFeeCents) {
        this.db = db;
        this.provider = provider;
        this.mockFeeCents = mockFeeCents;
    }

    /** Creates the DeliveryJob for a freshly confirmed delivery order (same transaction). */
    public Job createJob(String orderId) {
        String externalId = "mock-" + UUID.randomUUID().toString().substring(0, 8);
        String trackingUrl = "https://track.nanaskitchens.example/" + externalId;
        db.sql("""
                INSERT INTO "DeliveryJob" (id, "orderId", provider, "externalId", status, "trackingUrl", "feeCents")
                VALUES (:id, :orderId, :provider, :externalId, 'courier_assigned', :trackingUrl, :feeCents)
                """)
                .param("id", UUID.randomUUID().toString())
                .param("orderId", orderId)
                .param("provider", provider)
                .param("externalId", externalId)
                .param("trackingUrl", trackingUrl)
                .param("feeCents", mockFeeCents)
                .update();
        return new Job(provider, "courier_assigned", trackingUrl, mockFeeCents);
    }
}
