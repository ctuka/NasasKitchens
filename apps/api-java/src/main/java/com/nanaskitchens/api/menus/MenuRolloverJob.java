package com.nanaskitchens.api.menus;

import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Daily menu rollover — kills the "UTC midnight trap" (CLAUDE.md): menus are bound to the
 * UTC date, so after midnight every kitchen searched as "no portions" until someone re-ran
 * the seed by hand. This job republishes each kitchen's latest published menu for the new
 * UTC day (fresh portions, same ready windows). Idempotent: a kitchen that already has ANY
 * MenuDay for today — draft or published, seller-authored or rolled over — is skipped, so
 * sellers who manage their own menus are never overwritten.
 *
 * Dev/demo behaviour by default; disable with MENU_DAILY_ROLLOVER=false once sellers are
 * expected to publish every day themselves.
 */
@Component
public class MenuRolloverJob {

    private static final Logger log = LoggerFactory.getLogger(MenuRolloverJob.class);

    private final JdbcClient db;
    private final boolean enabled;

    public MenuRolloverJob(JdbcClient db, @Value("${app.menus.daily-rollover:true}") boolean enabled) {
        this.db = db;
        this.enabled = enabled;
    }

    private record Source(String kitchenId, String menuDayId, String readyWindows) {
    }

    /** Every 30 min (initial run 15 s after boot) — cheap no-op when nothing needs rolling. */
    @Scheduled(initialDelayString = "${app.menus.rollover-initial-delay-ms:15000}",
            fixedDelayString = "${app.menus.rollover-delay-ms:1800000}")
    @Transactional
    public void rolloverPublishedMenus() {
        if (!enabled) {
            return;
        }
        // Latest published menu from a previous UTC day, for kitchens with nothing today.
        // Date math stays in SQL as UTC (CURRENT_DATE would use the JDBC session zone).
        List<Source> sources = db.sql("""
                SELECT k.id AS kitchen_id, md.id AS menu_day_id, md."readyWindows"::text AS ready_windows
                FROM "Kitchen" k
                JOIN LATERAL (
                    SELECT id, "readyWindows" FROM "MenuDay"
                    WHERE "kitchenId" = k.id AND status = 'published'
                      AND date < (now() AT TIME ZONE 'UTC')::date
                    ORDER BY date DESC
                    LIMIT 1
                ) md ON true
                WHERE NOT EXISTS (
                    SELECT 1 FROM "MenuDay" t
                    WHERE t."kitchenId" = k.id AND t.date = (now() AT TIME ZONE 'UTC')::date
                )
                """)
                .query((rs, n) -> new Source(
                        rs.getString("kitchen_id"), rs.getString("menu_day_id"), rs.getString("ready_windows")))
                .list();
        for (Source source : sources) {
            String newMenuDayId = UUID.randomUUID().toString();
            db.sql("""
                    INSERT INTO "MenuDay" (id, "kitchenId", date, status, "readyWindows")
                    VALUES (:id, :kitchenId, (now() AT TIME ZONE 'UTC')::date, 'published', :readyWindows::jsonb)
                    """)
                    .param("id", newMenuDayId)
                    .param("kitchenId", source.kitchenId())
                    .param("readyWindows", source.readyWindows())
                    .update();
            // Fresh inventory: portionsRemaining resets to portionsTotal for the new day.
            db.sql("""
                    INSERT INTO "MenuItem" (id, "menuDayId", "dishId", "portionsTotal", "portionsRemaining")
                    SELECT gen_random_uuid()::text, :newMenuDayId, "dishId", "portionsTotal", "portionsTotal"
                    FROM "MenuItem" WHERE "menuDayId" = :sourceMenuDayId
                    """)
                    .param("newMenuDayId", newMenuDayId)
                    .param("sourceMenuDayId", source.menuDayId())
                    .update();
            db.sql("""
                    INSERT INTO "AuditLog" (id, actor, entity, action, "after")
                    VALUES (:id, 'menu-rollover', :entity, 'rollover_publish', :after::jsonb)
                    """)
                    .param("id", UUID.randomUUID().toString())
                    .param("entity", "MenuDay:" + newMenuDayId)
                    .param("after", "{\"sourceMenuDayId\":\"" + source.menuDayId() + "\"}")
                    .update();
        }
        if (!sources.isEmpty()) {
            log.info("Menu rollover: republished today's menu for {} kitchen(s)", sources.size());
        }
    }
}
