package com.nanaskitchens.api.admin;

import com.nanaskitchens.api.audit.AuditLog;
import com.nanaskitchens.api.audit.AuditLogRepository;
import com.nanaskitchens.api.notifications.NotificationsService;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Story 7.3 — admin tools: inspector provisioning (platform-invited, no open signup),
 * a visits overview, and the score-dispute queue promised by Story 7.2 AC3. Resolving a
 * dispute never edits the HygieneScore row — "annul" only clears the kitchen badge so
 * the profile honestly shows "Not yet inspected" until a re-inspection.
 */
@Service
public class AdminService {

    private final JdbcClient db;
    private final PasswordEncoder passwordEncoder;
    private final NotificationsService notifications;
    private final AuditLogRepository auditLogRepository;

    public AdminService(
            JdbcClient db,
            PasswordEncoder passwordEncoder,
            NotificationsService notifications,
            AuditLogRepository auditLogRepository) {
        this.db = db;
        this.passwordEncoder = passwordEncoder;
        this.notifications = notifications;
        this.auditLogRepository = auditLogRepository;
    }

    // ── Inspector management ─────────────────────────────────────────────────

    public List<Map<String, Object>> listInspectors() {
        return db.sql("""
                SELECT u.id, u.email, u."createdAt",
                       COALESCE(SUM(CASE WHEN v.status = 'assigned' THEN 1 ELSE 0 END), 0)::int AS assigned,
                       COALESCE(SUM(CASE WHEN v.status = 'scored' THEN 1 ELSE 0 END), 0)::int AS scored
                FROM "User" u
                LEFT JOIN "InspectionVisit" v ON v."inspectorId" = u.id
                WHERE u.role = 'inspector'
                GROUP BY u.id, u.email, u."createdAt"
                ORDER BY u."createdAt"
                """)
                .query((rs, n) -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("id", rs.getString("id"));
                    row.put("email", rs.getString("email"));
                    row.put("assigned", rs.getInt("assigned"));
                    row.put("scored", rs.getInt("scored"));
                    return row;
                })
                .list();
    }

    /** Platform-invited inspector accounts (register endpoint only allows buyer/seller). */
    @Transactional
    public Map<String, Object> inviteInspector(String adminId, String email, String password) {
        Integer exists = db.sql("SELECT count(*)::int FROM \"User\" WHERE email = :email")
                .param("email", email)
                .query(Integer.class)
                .single();
        if (exists > 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "EMAIL_TAKEN");
        }
        String id = UUID.randomUUID().toString();
        db.sql("""
                INSERT INTO "User" (id, role, email, "passwordHash")
                VALUES (:id, 'inspector', :email, :passwordHash)
                """)
                .param("id", id)
                .param("email", email)
                .param("passwordHash", passwordEncoder.encode(password))
                .update();
        audit(adminId, "User:" + id, "invite_inspector");
        return Map.of("id", id, "email", email);
    }

    // ── Visits overview ──────────────────────────────────────────────────────

    public List<Map<String, Object>> listVisits() {
        return db.sql("""
                SELECT v.id, v.status, v."scheduledAt", k.name AS kitchen_name, u.email AS inspector_email,
                       hs.total AS score_total
                FROM "InspectionVisit" v
                JOIN "Kitchen" k ON k.id = v."kitchenId"
                JOIN "User" u ON u.id = v."inspectorId"
                LEFT JOIN "HygieneScore" hs ON hs."visitId" = v.id
                ORDER BY v."scheduledAt" DESC
                LIMIT 100
                """)
                .query((rs, n) -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("id", rs.getString("id"));
                    row.put("kitchenName", rs.getString("kitchen_name"));
                    row.put("inspectorEmail", rs.getString("inspector_email"));
                    row.put("scheduledAt", rs.getTimestamp("scheduledAt").toLocalDateTime());
                    row.put("status", rs.getString("status"));
                    row.put("scoreTotal", rs.getObject("score_total"));
                    return row;
                })
                .list();
    }

    /** Admins pick kitchens by name when assigning — attestation/geo don't matter here. */
    public List<Map<String, Object>> listKitchens() {
        return db.sql("SELECT id, name FROM \"Kitchen\" ORDER BY name")
                .query((rs, n) -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("id", rs.getString("id"));
                    row.put("name", rs.getString("name"));
                    return row;
                })
                .list();
    }

    // ── Score disputes (Story 7.2 AC3 hand-off) ──────────────────────────────

    /** Seller opens a dispute against their current badge; one open dispute at a time. */
    @Transactional
    public Map<String, Object> openDispute(String sellerId, String kitchenId, String reason) {
        record Row(String sellerId, Integer score) {
        }
        Row kitchen = db.sql("SELECT \"sellerId\", \"hygieneScoreTotal\" FROM \"Kitchen\" WHERE id = :id")
                .param("id", kitchenId)
                .query((rs, n) -> new Row(rs.getString("sellerId"), rs.getObject("hygieneScoreTotal", Integer.class)))
                .optional()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "KITCHEN_NOT_FOUND"));
        if (!kitchen.sellerId().equals(sellerId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
        if (kitchen.score() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "NO_SCORE_TO_DISPUTE");
        }
        Integer open = db.sql("""
                SELECT count(*)::int FROM "ScoreDispute" WHERE "kitchenId" = :id AND status = 'open'
                """)
                .param("id", kitchenId)
                .query(Integer.class)
                .single();
        if (open > 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "DISPUTE_ALREADY_OPEN");
        }
        String id = UUID.randomUUID().toString();
        db.sql("""
                INSERT INTO "ScoreDispute" (id, "kitchenId", "openedById", reason)
                VALUES (:id, :kitchenId, :openedById, :reason)
                """)
                .param("id", id)
                .param("kitchenId", kitchenId)
                .param("openedById", sellerId)
                .param("reason", reason.trim())
                .update();
        audit(sellerId, "ScoreDispute:" + id, "open_score_dispute");
        return getDispute(id);
    }

    public List<Map<String, Object>> listDisputes(String status) {
        return db.sql("""
                SELECT d.id, d."kitchenId", d.reason, d.status, d."adminNote", d."createdAt", d."resolvedAt",
                       k.name AS kitchen_name, k."hygieneScoreTotal" AS current_score
                FROM "ScoreDispute" d JOIN "Kitchen" k ON k.id = d."kitchenId"
                WHERE (:status::text IS NULL OR d.status = :status)
                ORDER BY d."createdAt" DESC
                LIMIT 100
                """)
                .param("status", status, java.sql.Types.VARCHAR)
                .query((rs, n) -> disputeRow(rs))
                .list();
    }

    /** dismissed → score stands; annulled → badge cleared until re-inspection. */
    @Transactional
    public Map<String, Object> resolveDispute(String adminId, String disputeId, String resolution, String note) {
        if (!"dismissed".equals(resolution) && !"annulled".equals(resolution)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "INVALID_RESOLUTION");
        }
        record Row(String kitchenId, String openedById, String status, String kitchenName) {
        }
        Row dispute = db.sql("""
                SELECT d."kitchenId", d."openedById", d.status, k.name AS kitchen_name
                FROM "ScoreDispute" d JOIN "Kitchen" k ON k.id = d."kitchenId"
                WHERE d.id = :id FOR UPDATE OF d
                """)
                .param("id", disputeId)
                .query((rs, n) -> new Row(rs.getString("kitchenId"), rs.getString("openedById"),
                        rs.getString("status"), rs.getString("kitchen_name")))
                .optional()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "DISPUTE_NOT_FOUND"));
        if (!"open".equals(dispute.status())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "ALREADY_RESOLVED");
        }

        db.sql("""
                UPDATE "ScoreDispute" SET status = :status, "adminNote" = :note, "resolvedAt" = now()
                WHERE id = :id
                """)
                .param("status", resolution)
                .param("note", note == null || note.isBlank() ? null : note.trim())
                .param("id", disputeId)
                .update();

        if ("annulled".equals(resolution)) {
            db.sql("""
                    UPDATE "Kitchen" SET "hygieneScoreTotal" = NULL, "hygieneScoredAt" = NULL WHERE id = :id
                    """)
                    .param("id", dispute.kitchenId())
                    .update();
        }

        notifications.notify(dispute.openedById(), "score_dispute_resolved", "Score dispute resolved",
                "annulled".equals(resolution)
                        ? dispute.kitchenName() + "'s hygiene badge was withdrawn pending re-inspection."
                        : "Your dispute was reviewed — the score on " + dispute.kitchenName() + " stands.",
                Map.of("kitchenId", dispute.kitchenId()));
        audit(adminId, "ScoreDispute:" + disputeId, "resolve_score_dispute_" + resolution);
        return getDispute(disputeId);
    }

    private Map<String, Object> getDispute(String id) {
        return db.sql("""
                SELECT d.id, d."kitchenId", d.reason, d.status, d."adminNote", d."createdAt", d."resolvedAt",
                       k.name AS kitchen_name, k."hygieneScoreTotal" AS current_score
                FROM "ScoreDispute" d JOIN "Kitchen" k ON k.id = d."kitchenId"
                WHERE d.id = :id
                """)
                .param("id", id)
                .query((rs, n) -> disputeRow(rs))
                .single();
    }

    private static Map<String, Object> disputeRow(java.sql.ResultSet rs) throws java.sql.SQLException {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", rs.getString("id"));
        row.put("kitchenId", rs.getString("kitchenId"));
        row.put("kitchenName", rs.getString("kitchen_name"));
        row.put("currentScore", rs.getObject("current_score"));
        row.put("reason", rs.getString("reason"));
        row.put("status", rs.getString("status"));
        row.put("adminNote", rs.getString("adminNote"));
        row.put("createdAt", rs.getTimestamp("createdAt").toLocalDateTime());
        row.put("resolvedAt", rs.getTimestamp("resolvedAt") == null
                ? null
                : rs.getTimestamp("resolvedAt").toLocalDateTime());
        return row;
    }

    private void audit(String actor, String entity, String action) {
        AuditLog log = new AuditLog();
        log.setActor(actor);
        log.setEntity(entity);
        log.setAction(action);
        auditLogRepository.save(log);
    }
}
