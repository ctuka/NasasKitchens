package com.nanaskitchens.api.delivery;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.json.JsonMapper;

/** Story 4.2 — DeliveryJob creation on Ready, webhook status updates (verified + idempotent). */
@Service
public class DeliveryService {

    /** Forward-only ranks make webhook replays no-ops (AC3 idempotency without an event store). */
    private static final Map<String, Integer> STATUS_RANK = Map.of(
            "created", 0, "courier_assigned", 1, "picked_up", 2, "delivered", 3, "cancelled", 3);

    private final JdbcClient db;
    private final DeliveryProvider provider;
    private final JsonMapper jsonMapper;
    private final byte[] webhookSecret;

    public DeliveryService(
            JdbcClient db,
            DeliveryProvider provider,
            JsonMapper jsonMapper,
            @Value("${app.delivery.webhook-secret}") String webhookSecret) {
        this.db = db;
        this.provider = provider;
        this.jsonMapper = jsonMapper;
        this.webhookSecret = webhookSecret.getBytes(StandardCharsets.UTF_8);
    }

    /** Called when the seller marks a delivery-fulfillment order Ready (AC2). */
    @Transactional
    public Map<String, Object> createForOrder(String orderId, String actor) {
        Map<String, Object> existing = findByOrderId(orderId);
        if (existing != null) {
            return existing; // ready re-triggered — keep the existing job
        }
        DeliveryProvider.Quote quote = provider.quote(null, orderId); // AC1: fee via provider quote
        DeliveryProvider.CreatedDelivery created = provider.create(quote.quoteId(), orderId);
        db.sql("""
                INSERT INTO "DeliveryJob" (id, "orderId", provider, "externalId", status, "trackingUrl", "feeCents")
                VALUES (:id, :orderId, :provider, :externalId, 'created', :trackingUrl, :feeCents)
                """)
                .param("id", UUID.randomUUID().toString())
                .param("orderId", orderId)
                .param("provider", provider.name())
                .param("externalId", created.externalId())
                .param("trackingUrl", created.trackingUrl())
                .param("feeCents", created.feeCents())
                .update();
        audit(actor, orderId, "delivery_create", Map.of(
                "provider", provider.name(), "externalId", created.externalId(), "feeCents", created.feeCents()));
        return findByOrderId(orderId);
    }

    public record WebhookEvent(String eventId, String externalId, String status) {
    }

    /** AC3 — signature-verified, idempotent webhook processing. Returns the applied transition. */
    @Transactional
    public Map<String, Object> processWebhook(String providerName, byte[] rawBody, String signature) {
        verifySignature(rawBody, signature);
        WebhookEvent event = jsonMapper.readValue(rawBody, WebhookEvent.class);
        Integer newRank = STATUS_RANK.get(event.status());
        if (newRank == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "UNKNOWN_STATUS");
        }
        record Job(String id, String orderId, String status) {
        }
        Job job = db.sql("""
                SELECT id, "orderId", status FROM "DeliveryJob"
                WHERE "externalId" = :externalId AND provider = :provider
                FOR UPDATE
                """)
                .param("externalId", event.externalId())
                .param("provider", providerName)
                .query((rs, n) -> new Job(rs.getString("id"), rs.getString("orderId"), rs.getString("status")))
                .optional()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "DELIVERY_NOT_FOUND"));

        int currentRank = STATUS_RANK.getOrDefault(job.status(), 0);
        if (newRank <= currentRank) {
            return Map.of("applied", false, "status", job.status()); // replay / out-of-order: no-op
        }
        db.sql("UPDATE \"DeliveryJob\" SET status = :status WHERE id = :id")
                .param("status", event.status())
                .param("id", job.id())
                .update();
        if ("delivered".equals(event.status())) {
            db.sql("UPDATE \"Order\" SET status = 'completed' WHERE id = :id AND status = 'ready'")
                    .param("id", job.orderId())
                    .update();
        }
        audit("webhook:" + providerName, job.orderId(), "delivery_status",
                Map.of("eventId", event.eventId(), "from", job.status(), "to", event.status()));
        return Map.of("applied", true, "status", event.status());
    }

    public Map<String, Object> findByOrderId(String orderId) {
        return db.sql("""
                SELECT provider::text AS provider, status, "trackingUrl", "feeCents"
                FROM "DeliveryJob" WHERE "orderId" = :orderId
                """)
                .param("orderId", orderId)
                .query((rs, n) -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("provider", rs.getString("provider"));
                    row.put("status", rs.getString("status"));
                    row.put("trackingUrl", rs.getString("trackingUrl"));
                    row.put("feeCents", rs.getInt("feeCents"));
                    return row;
                })
                .optional()
                .orElse(null);
    }

    private void verifySignature(byte[] rawBody, String signature) {
        if (signature == null || signature.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "SIGNATURE_MISSING");
        }
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(webhookSecret, "HmacSHA256"));
            String expected = HexFormat.of().formatHex(mac.doFinal(rawBody));
            if (!MessageDigest.isEqual(
                    expected.getBytes(StandardCharsets.UTF_8), signature.getBytes(StandardCharsets.UTF_8))) {
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "SIGNATURE_INVALID");
            }
        } catch (java.security.GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    private void audit(String actor, String orderId, String action, Map<String, Object> after) {
        db.sql("""
                INSERT INTO "AuditLog" (id, actor, entity, action, "after")
                VALUES (:id, :actor, :entity, :action, :after::jsonb)
                """)
                .param("id", UUID.randomUUID().toString())
                .param("actor", actor)
                .param("entity", "Order:" + orderId)
                .param("action", action)
                .param("after", jsonMapper.writeValueAsString(after))
                .update();
    }
}
