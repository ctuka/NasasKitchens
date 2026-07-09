package com.nanaskitchens.api.community;

import com.nanaskitchens.api.notifications.NotificationsService;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Story 6.3 (FR18): a buyer submits a dish/cuisine request to a kitchen; the seller
 * accepts (notifying the requester) or declines. status: open | accepted | declined —
 * only the owning seller can transition an open request, and only once.
 */
@Service
public class DishRequestsService {

    private final JdbcClient db;
    private final NotificationsService notifications;

    public DishRequestsService(JdbcClient db, NotificationsService notifications) {
        this.db = db;
        this.notifications = notifications;
    }

    @Transactional
    public Map<String, Object> create(String buyerId, String kitchenId, String text) {
        String kitchenName = db.sql("SELECT name FROM \"Kitchen\" WHERE id = :id")
                .param("id", kitchenId)
                .query(String.class)
                .optional()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "KITCHEN_NOT_FOUND"));
        String sellerId = db.sql("SELECT \"sellerId\" FROM \"Kitchen\" WHERE id = :id")
                .param("id", kitchenId)
                .query(String.class)
                .single();

        String id = UUID.randomUUID().toString();
        db.sql("""
                INSERT INTO "DishRequest" (id, "kitchenId", "buyerId", text)
                VALUES (:id, :kitchenId, :buyerId, :text)
                """)
                .param("id", id)
                .param("kitchenId", kitchenId)
                .param("buyerId", buyerId)
                .param("text", text.trim())
                .update();

        notifications.notify(sellerId, "dish_request", "New dish request",
                "A buyer asked " + kitchenName + " for something new.", Map.of("kitchenId", kitchenId));
        return getById(id);
    }

    /** The seller's inbox for one kitchen (owner only). */
    public List<Map<String, Object>> listForKitchen(String sellerId, String kitchenId, String status) {
        requireOwnedKitchen(sellerId, kitchenId);
        return db.sql("""
                SELECT id, "kitchenId", "buyerId", text, status, "sellerNote", "createdAt"
                FROM "DishRequest"
                WHERE "kitchenId" = :kitchenId AND (:status::text IS NULL OR status = :status)
                ORDER BY "createdAt" DESC
                LIMIT 100
                """)
                .param("kitchenId", kitchenId)
                .param("status", status, java.sql.Types.VARCHAR)
                .query((rs, n) -> rowToMap(rs))
                .list();
    }

    /** The buyer's own submitted requests across kitchens. */
    public List<Map<String, Object>> listMine(String buyerId) {
        return db.sql("""
                SELECT dr.id, dr."kitchenId", dr."buyerId", dr.text, dr.status, dr."sellerNote",
                       dr."createdAt", k.name AS kitchen_name
                FROM "DishRequest" dr JOIN "Kitchen" k ON k.id = dr."kitchenId"
                WHERE dr."buyerId" = :buyerId
                ORDER BY dr."createdAt" DESC
                LIMIT 100
                """)
                .param("buyerId", buyerId)
                .query((rs, n) -> {
                    Map<String, Object> row = rowToMap(rs);
                    row.put("kitchenName", rs.getString("kitchen_name"));
                    return row;
                })
                .list();
    }

    @Transactional
    public Map<String, Object> respond(String sellerId, String requestId, String target, String note) {
        if (!"accepted".equals(target) && !"declined".equals(target)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "INVALID_STATUS");
        }
        record Row(String kitchenId, String buyerId, String status, String kitchenName) {
        }
        Row req = db.sql("""
                SELECT dr."kitchenId", dr."buyerId", dr.status, k.name AS kitchen_name
                FROM "DishRequest" dr JOIN "Kitchen" k ON k.id = dr."kitchenId"
                WHERE dr.id = :id
                """)
                .param("id", requestId)
                .query((rs, n) -> new Row(rs.getString("kitchenId"), rs.getString("buyerId"),
                        rs.getString("status"), rs.getString("kitchen_name")))
                .optional()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "REQUEST_NOT_FOUND"));
        requireOwnedKitchen(sellerId, req.kitchenId());
        if (!"open".equals(req.status())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "ALREADY_RESOLVED");
        }

        db.sql("UPDATE \"DishRequest\" SET status = :status, \"sellerNote\" = :note WHERE id = :id")
                .param("status", target)
                .param("note", note == null || note.isBlank() ? null : note.trim())
                .param("id", requestId)
                .update();

        // FR18 + FR22: the requester is notified either way; acceptance is the headline.
        if ("accepted".equals(target)) {
            notifications.notify(req.buyerId(), "dish_request_accepted", "Request accepted!",
                    req.kitchenName() + " is adding your requested dish to a future menu.",
                    Map.of("kitchenId", req.kitchenId()));
        } else {
            notifications.notify(req.buyerId(), "dish_request_declined", "Request update",
                    req.kitchenName() + " can't add that one right now.",
                    Map.of("kitchenId", req.kitchenId()));
        }
        return getById(requestId);
    }

    private Map<String, Object> getById(String id) {
        return db.sql("""
                SELECT id, "kitchenId", "buyerId", text, status, "sellerNote", "createdAt"
                FROM "DishRequest" WHERE id = :id
                """)
                .param("id", id)
                .query((rs, n) -> rowToMap(rs))
                .single();
    }

    private static Map<String, Object> rowToMap(java.sql.ResultSet rs) throws java.sql.SQLException {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", rs.getString("id"));
        row.put("kitchenId", rs.getString("kitchenId"));
        row.put("buyerId", rs.getString("buyerId"));
        row.put("text", rs.getString("text"));
        row.put("status", rs.getString("status"));
        row.put("sellerNote", rs.getString("sellerNote"));
        row.put("createdAt", rs.getTimestamp("createdAt").toLocalDateTime());
        return row;
    }

    private void requireOwnedKitchen(String sellerId, String kitchenId) {
        String ownerId = db.sql("SELECT \"sellerId\" FROM \"Kitchen\" WHERE id = :id")
                .param("id", kitchenId)
                .query(String.class)
                .optional()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "KITCHEN_NOT_FOUND"));
        if (!ownerId.equals(sellerId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
    }
}
