package com.nanaskitchens.api.delivery;

import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * Public tracking lookup by the courier job's externalId. Exposes courier progress only —
 * no buyer identity, no address, no order contents — so the link is safe to share.
 */
@RestController
public class DeliveryController {

    private final JdbcClient db;

    public DeliveryController(JdbcClient db) {
        this.db = db;
    }

    @GetMapping("/track/{externalId}")
    public Map<String, Object> track(@PathVariable String externalId) {
        return db.sql("""
                SELECT dj."externalId", dj.provider::text AS provider, dj.status, dj."feeCents",
                       o."readySlot", k.name AS kitchen_name
                FROM "DeliveryJob" dj
                JOIN "Order" o ON o.id = dj."orderId"
                JOIN "Kitchen" k ON k.id = o."kitchenId"
                WHERE dj."externalId" = :externalId
                """)
                .param("externalId", externalId)
                .query((rs, n) -> Map.<String, Object>of(
                        "externalId", rs.getString("externalId"),
                        "provider", rs.getString("provider"),
                        "status", rs.getString("status"),
                        "feeCents", rs.getInt("feeCents"),
                        "readySlot", rs.getTimestamp("readySlot").toLocalDateTime().toString(),
                        "kitchenName", rs.getString("kitchen_name")))
                .optional()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "TRACKING_NOT_FOUND"));
    }
}
