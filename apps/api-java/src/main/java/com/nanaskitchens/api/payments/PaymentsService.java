package com.nanaskitchens.api.payments;

import com.nanaskitchens.api.inventory.InventoryService;
import com.nanaskitchens.api.inventory.PortionsChanged;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

/**
 * Story 3.4 — settles pending orders from payment-provider events. Both transitions are
 * idempotent (guarded by the current status under FOR UPDATE), so webhook replays are
 * no-ops, matching the delivery webhook's contract.
 */
@Service
public class PaymentsService {

    private final JdbcClient db;
    private final InventoryService inventory;
    private final ApplicationEventPublisher eventPublisher;
    private final JsonMapper jsonMapper;

    public PaymentsService(
            JdbcClient db,
            InventoryService inventory,
            ApplicationEventPublisher eventPublisher,
            JsonMapper jsonMapper) {
        this.db = db;
        this.inventory = inventory;
        this.eventPublisher = eventPublisher;
        this.jsonMapper = jsonMapper;
    }

    private record OrderRow(String id, String status) {
    }

    /** payment_intent.succeeded → pending order becomes confirmed (enters the seller flow). */
    @Transactional
    public Map<String, Object> markPaid(String paymentIntentId) {
        OrderRow order = lockByIntent(paymentIntentId);
        if (order == null) {
            return Map.of("applied", false, "reason", "ORDER_NOT_FOUND");
        }
        if (!"pending".equals(order.status())) {
            // Replay, or the order was cancelled while the payment sheet was open — the money
            // is captured with no live order; surface it in the audit trail for manual refund.
            String reason = "confirmed".equals(order.status()) ? "REPLAY" : "ORPHANED_PAYMENT";
            audit("webhook:payments", order.id(), "payment_orphan_check",
                    Map.of("paymentIntentId", paymentIntentId, "status", order.status(), "reason", reason));
            return Map.of("applied", false, "reason", reason);
        }
        db.sql("UPDATE \"Order\" SET status = 'confirmed' WHERE id = :id").param("id", order.id()).update();
        audit("webhook:payments", order.id(), "payment_succeeded", Map.of("paymentIntentId", paymentIntentId));
        return Map.of("applied", true, "orderId", order.id(), "status", "confirmed");
    }

    /**
     * payment_intent.payment_failed / canceled, or checkout abandonment (sweeper) —
     * the compensating restore of Story 3.3 AC3: portions go back in the same transaction
     * that cancels the order.
     */
    @Transactional
    public Map<String, Object> releaseFailedPayment(String paymentIntentId, String reason) {
        OrderRow order = lockByIntent(paymentIntentId);
        if (order == null || !"pending".equals(order.status())) {
            return Map.of("applied", false);
        }
        record ItemQty(String menuItemId, int qty) {
        }
        List<ItemQty> items = db.sql("SELECT \"menuItemId\", qty FROM \"OrderItem\" WHERE \"orderId\" = :id")
                .param("id", order.id())
                .query((rs, n) -> new ItemQty(rs.getString("menuItemId"), rs.getInt("qty")))
                .list();
        for (ItemQty item : items) {
            inventory.restore(item.menuItemId(), item.qty());
        }
        db.sql("UPDATE \"Order\" SET status = 'cancelled' WHERE id = :id").param("id", order.id()).update();
        audit("webhook:payments", order.id(), "payment_failed",
                Map.of("paymentIntentId", paymentIntentId, "reason", reason));
        eventPublisher.publishEvent(new PortionsChanged(items.stream().map(ItemQty::menuItemId).toList()));
        return Map.of("applied", true, "orderId", order.id(), "status", "cancelled");
    }

    private OrderRow lockByIntent(String paymentIntentId) {
        return db.sql("SELECT id, status FROM \"Order\" WHERE \"paymentIntentId\" = :pi FOR UPDATE")
                .param("pi", paymentIntentId)
                .query((rs, n) -> new OrderRow(rs.getString("id"), rs.getString("status")))
                .optional()
                .orElse(null);
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
