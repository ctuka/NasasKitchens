package com.nanaskitchens.api.notifications;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Reads/writes a user's external-channel opt-outs (Story 4.4 follow-up). */
@Service
public class NotificationPreferencesService {

    private final JdbcClient db;

    public NotificationPreferencesService(JdbcClient db) {
        this.db = db;
    }

    /** The disabled "category:channel" keys for a user (empty when no row / all enabled). */
    public Set<String> disabledFor(String userId) {
        return db.sql("SELECT disabled FROM \"NotificationPreference\" WHERE \"userId\" = :id")
                .param("id", userId)
                .query((rs, n) -> {
                    java.sql.Array arr = rs.getArray("disabled");
                    return arr == null ? Set.<String>of() : Set.copyOf(Arrays.asList((String[]) arr.getArray()));
                })
                .optional()
                .orElse(Set.of());
    }

    /** The settings grid: categories × channels, each with an enabled flag for the UI. */
    public Map<String, Object> grid(String userId) {
        Set<String> disabled = disabledFor(userId);
        List<Map<String, Object>> categories = NotificationPreferences.CATEGORIES.stream()
                .map(category -> {
                    Map<String, Object> channels = new LinkedHashMap<>();
                    NotificationPreferences.CHANNELS.keySet()
                            .forEach(ch -> channels.put(ch, !disabled.contains(category + ":" + ch)));
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("category", category);
                    row.put("channels", channels);
                    return row;
                })
                .toList();
        return Map.of("channels", List.copyOf(NotificationPreferences.CHANNELS.keySet()), "categories", categories);
    }

    /** Replaces the disabled set; only well-formed "category:channel" keys are kept. */
    @Transactional
    public Map<String, Object> update(String userId, List<String> disabled) {
        List<String> clean = disabled == null ? List.of() : disabled.stream()
                .filter(k -> {
                    String[] parts = k.split(":", 2);
                    return parts.length == 2
                            && NotificationPreferences.CATEGORIES.contains(parts[0])
                            && NotificationPreferences.CHANNELS.containsKey(parts[1]);
                })
                .distinct()
                .toList();
        db.sql("""
                INSERT INTO "NotificationPreference" ("userId", disabled, "updatedAt")
                VALUES (:id, :disabled, now())
                ON CONFLICT ("userId") DO UPDATE SET disabled = :disabled, "updatedAt" = now()
                """)
                .param("id", userId)
                .param("disabled", clean.toArray(String[]::new))
                .update();
        return grid(userId);
    }
}
