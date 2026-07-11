package com.nanaskitchens.api.trust;

import com.nanaskitchens.api.audit.AuditLog;
import com.nanaskitchens.api.audit.AuditLogRepository;
import com.nanaskitchens.api.kitchens.AddressCrypto;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.json.JsonMapper;

/**
 * Story 7.2 (FR20): admin-assigned inspection visits + structured hygiene scoring.
 * Besides the order serializer (FR10), this is the ONLY place kitchen addresses decrypt —
 * and only into an inspector's own assigned-visit list (NFR5 scoping, AC1). Scores are
 * submit-once (AC3); corrections go through the admin dispute flow (Story 7.3).
 */
@Service
public class InspectionsService {

    /** AC2 — fixed sub-score keys, 0–20 each, summing to the 0–100 badge total. */
    public static final List<String> SUB_SCORE_KEYS = List.of(
            "storage", "prepSurfaces", "temperatureControl", "personalHygiene", "documentation");

    private final JdbcClient db;
    private final AddressCrypto addressCrypto;
    private final AuditLogRepository auditLogRepository;
    private final JsonMapper jsonMapper;

    public InspectionsService(
            JdbcClient db,
            AddressCrypto addressCrypto,
            AuditLogRepository auditLogRepository,
            JsonMapper jsonMapper) {
        this.db = db;
        this.addressCrypto = addressCrypto;
        this.auditLogRepository = auditLogRepository;
        this.jsonMapper = jsonMapper;
    }

    /** AC1 — admin assigns a visit; the inspector is addressed by email (platform-invited). */
    @Transactional
    public Map<String, Object> assign(String adminId, String kitchenId, String inspectorEmail, String scheduledAt) {
        String kitchenName = db.sql("SELECT name FROM \"Kitchen\" WHERE id = :id")
                .param("id", kitchenId)
                .query(String.class)
                .optional()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "KITCHEN_NOT_FOUND"));
        String inspectorId = db.sql("""
                SELECT id FROM "User" WHERE email = :email AND role = 'inspector'
                """)
                .param("email", inspectorEmail)
                .query(String.class)
                .optional()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "INSPECTOR_NOT_FOUND"));

        LocalDateTime when;
        try {
            when = LocalDateTime.parse(scheduledAt.length() > 19 ? scheduledAt.substring(0, 19) : scheduledAt);
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "DATE_INVALID");
        }

        String id = UUID.randomUUID().toString();
        db.sql("""
                INSERT INTO "InspectionVisit" (id, "kitchenId", "inspectorId", "scheduledAt")
                VALUES (:id, :kitchenId, :inspectorId, :scheduledAt)
                """)
                .param("id", id)
                .param("kitchenId", kitchenId)
                .param("inspectorId", inspectorId)
                .param("scheduledAt", when)
                .update();

        AuditLog log = new AuditLog();
        log.setActor(adminId);
        log.setEntity("InspectionVisit:" + id);
        log.setAction("assign_inspection");
        auditLogRepository.save(log);

        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", id);
        row.put("kitchenId", kitchenId);
        row.put("kitchenName", kitchenName);
        row.put("inspectorEmail", inspectorEmail);
        row.put("scheduledAt", when);
        row.put("status", "assigned");
        return row;
    }

    /** AC1 — the inspector's own visits; street address decrypts here and nowhere else. */
    public List<Map<String, Object>> assignedVisits(String inspectorId) {
        return db.sql("""
                SELECT v.id, v."kitchenId", v."scheduledAt", v.status, v."createdAt",
                       k.name AS kitchen_name, k."cuisineTag", k."addressEncrypted",
                       hs.total AS score_total, hs."submittedAt" AS score_submitted_at
                FROM "InspectionVisit" v
                JOIN "Kitchen" k ON k.id = v."kitchenId"
                LEFT JOIN "HygieneScore" hs ON hs."visitId" = v.id
                WHERE v."inspectorId" = :inspectorId
                ORDER BY v."scheduledAt" ASC
                """)
                .param("inspectorId", inspectorId)
                .query((rs, n) -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("id", rs.getString("id"));
                    row.put("kitchenId", rs.getString("kitchenId"));
                    row.put("kitchenName", rs.getString("kitchen_name"));
                    row.put("cuisineTag", rs.getString("cuisineTag"));
                    // NFR5: decryption is scoped to the assignee's own list (AC1).
                    row.put("address", addressCrypto.decrypt(rs.getString("addressEncrypted")));
                    row.put("scheduledAt", rs.getTimestamp("scheduledAt").toLocalDateTime());
                    row.put("status", rs.getString("status"));
                    Object total = rs.getObject("score_total");
                    row.put("scoreTotal", total);
                    row.put("scoreSubmittedAt", rs.getTimestamp("score_submitted_at") == null
                            ? null
                            : rs.getTimestamp("score_submitted_at").toLocalDateTime());
                    return row;
                })
                .list();
    }

    /** AC2 + AC3 — validate sub-scores, lock (submit-once), denormalize the badge. */
    @Transactional
    public Map<String, Object> submitScore(
            String inspectorId, String visitId, Map<String, Integer> subScores, List<String> photos) {
        record Visit(String kitchenId, String inspectorId, String status) {
        }
        Visit visit = db.sql("""
                SELECT "kitchenId", "inspectorId", status FROM "InspectionVisit" WHERE id = :id FOR UPDATE
                """)
                .param("id", visitId)
                .query((rs, n) -> new Visit(
                        rs.getString("kitchenId"), rs.getString("inspectorId"), rs.getString("status")))
                .optional()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "VISIT_NOT_FOUND"));
        if (!visit.inspectorId().equals(inspectorId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }

        int total = 0;
        for (String key : SUB_SCORE_KEYS) {
            Integer value = subScores.get(key);
            if (value == null || value < 0 || value > 20) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "SUBSCORE_INVALID:" + key);
            }
            total += value;
        }
        if (subScores.size() != SUB_SCORE_KEYS.size()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "SUBSCORE_UNKNOWN_KEY");
        }

        Integer existing = db.sql("SELECT count(*)::int FROM \"HygieneScore\" WHERE \"visitId\" = :id")
                .param("id", visitId)
                .query(Integer.class)
                .single();
        if (existing > 0 || "scored".equals(visit.status())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "ALREADY_SCORED"); // AC3 submit-once
        }

        String scoreId = UUID.randomUUID().toString();
        db.sql("""
                INSERT INTO "HygieneScore" (id, "visitId", total, "subScores", photos)
                VALUES (:id, :visitId, :total, :subScores::jsonb, :photos)
                """)
                .param("id", scoreId)
                .param("visitId", visitId)
                .param("total", total)
                .param("subScores", jsonMapper.writeValueAsString(subScores))
                .param("photos", (photos == null ? List.<String>of() : photos).toArray(String[]::new))
                .update();
        db.sql("UPDATE \"InspectionVisit\" SET status = 'scored' WHERE id = :id")
                .param("id", visitId)
                .update();
        // Badge denormalization — profile and search read these two columns (AC3/AC4).
        db.sql("""
                UPDATE "Kitchen" SET "hygieneScoreTotal" = :total, "hygieneScoredAt" = now() WHERE id = :id
                """)
                .param("total", total)
                .param("id", visit.kitchenId())
                .update();

        AuditLog log = new AuditLog();
        log.setActor(inspectorId);
        log.setEntity("HygieneScore:" + scoreId);
        log.setAction("submit_hygiene_score");
        auditLogRepository.save(log);

        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", scoreId);
        row.put("visitId", visitId);
        row.put("total", total);
        row.put("subScores", subScores);
        row.put("photos", photos == null ? List.of() : photos);
        return row;
    }

    /** The visit must belong to the caller before evidence uploads or scoring. */
    public void requireOwnVisit(String inspectorId, String visitId) {
        String ownerId = db.sql("SELECT \"inspectorId\" FROM \"InspectionVisit\" WHERE id = :id")
                .param("id", visitId)
                .query(String.class)
                .optional()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "VISIT_NOT_FOUND"));
        if (!ownerId.equals(inspectorId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
    }
}
