package com.nanaskitchens.api.notifications;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

/**
 * Story 4.4 (FR22) — order-lifecycle notifications. notify() is called from inside the
 * order/payment/delivery transactions, so the inbox row commits or rolls back with the
 * event that caused it; the external channel fires AFTER_COMMIT (NotificationDispatcher).
 */
@Service
public class NotificationsService {

    public record NotificationCreated(String userId, String type, String title, String body) {
    }

    private final JdbcClient db;
    private final ApplicationEventPublisher eventPublisher;
    private final JsonMapper jsonMapper;

    public NotificationsService(JdbcClient db, ApplicationEventPublisher eventPublisher, JsonMapper jsonMapper) {
        this.db = db;
        this.eventPublisher = eventPublisher;
        this.jsonMapper = jsonMapper;
    }

    public void notify(String userId, String type, String title, String body, Map<String, Object> data) {
        db.sql("""
                INSERT INTO "Notification" (id, "userId", type, title, body, data)
                VALUES (:id, :userId, :type, :title, :body, :data::jsonb)
                """)
                .param("id", UUID.randomUUID().toString())
                .param("userId", userId)
                .param("type", type)
                .param("title", title)
                .param("body", body)
                .param("data", data == null ? null : jsonMapper.writeValueAsString(data))
                .update();
        eventPublisher.publishEvent(new NotificationCreated(userId, type, title, body));
    }

    /** Latest 50 + unread count — enough for the web bell; no pagination yet. */
    public Map<String, Object> list(String userId) {
        List<Map<String, Object>> rows = db.sql("""
                SELECT id, type, title, body, data::text AS data, "readAt", "createdAt"
                FROM "Notification"
                WHERE "userId" = :userId
                ORDER BY "createdAt" DESC
                LIMIT 50
                """)
                .param("userId", userId)
                .query((rs, n) -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("id", rs.getString("id"));
                    row.put("type", rs.getString("type"));
                    row.put("title", rs.getString("title"));
                    row.put("body", rs.getString("body"));
                    String data = rs.getString("data");
                    row.put("data", data == null ? null : jsonMapper.readTree(data));
                    row.put("readAt", rs.getTimestamp("readAt") == null
                            ? null : rs.getTimestamp("readAt").toLocalDateTime());
                    row.put("createdAt", rs.getTimestamp("createdAt").toLocalDateTime());
                    return row;
                })
                .list();
        Integer unread = db.sql("""
                SELECT COUNT(*)::int FROM "Notification" WHERE "userId" = :userId AND "readAt" IS NULL
                """)
                .param("userId", userId)
                .query(Integer.class)
                .single();
        return Map.of("unreadCount", unread, "notifications", rows);
    }

    @Transactional
    public Map<String, Object> markAllRead(String userId) {
        int updated = db.sql("""
                UPDATE "Notification" SET "readAt" = now() WHERE "userId" = :userId AND "readAt" IS NULL
                """)
                .param("userId", userId)
                .update();
        return Map.of("marked", updated);
    }
}
