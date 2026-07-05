package com.nanaskitchens.api.inventory;

import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/**
 * Ported from apps/api/src/inventory (Story 2.3).
 * NFR1 / FR8: NEVER read-then-write. A single conditional UPDATE guarantees concurrent
 * orders can't drive portions below zero; rowcount 0 means PORTIONS_CONFLICT.
 */
@Service
public class InventoryService {

    private final JdbcClient db;

    public InventoryService(JdbcClient db) {
        this.db = db;
    }

    /** Must run inside the caller's transaction (order placement). */
    public void decrement(String menuItemId, int qty) {
        int updated = db.sql("""
                UPDATE "MenuItem"
                SET "portionsRemaining" = "portionsRemaining" - :qty
                WHERE id = :id AND "portionsRemaining" >= :qty
                """)
                .param("qty", qty)
                .param("id", menuItemId)
                .update();
        if (updated == 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "PORTIONS_CONFLICT:" + menuItemId);
        }
    }

    /** Refund is capped at portionsTotal; runs inside the caller's transaction (Story 2.3 AC2). */
    public void restore(String menuItemId, int qty) {
        db.sql("""
                UPDATE "MenuItem"
                SET "portionsRemaining" = LEAST("portionsRemaining" + :qty, "portionsTotal")
                WHERE id = :id
                """)
                .param("qty", qty)
                .param("id", menuItemId)
                .update();
    }

    public List<Map<String, Object>> remaining(List<String> menuItemIds) {
        if (menuItemIds == null || menuItemIds.isEmpty()) {
            return List.of();
        }
        return db.sql("""
                SELECT id, "portionsRemaining", "portionsTotal" FROM "MenuItem" WHERE id IN (:ids)
                """)
                .param("ids", menuItemIds)
                .query((rs, rowNum) -> Map.<String, Object>of(
                        "id", rs.getString("id"),
                        "portionsRemaining", rs.getInt("portionsRemaining"),
                        "portionsTotal", rs.getInt("portionsTotal")))
                .list();
    }
}
