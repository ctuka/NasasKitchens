package com.nanaskitchens.api.polls;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Story 6.2 (FR17): a seller polls buyers on candidate upcoming menu items. One vote per
 * buyer per poll is guaranteed by the PollVote unique index — a second vote is rejected,
 * never silently upserted. Vote tallies are computed on read from the raw rows.
 */
@Service
public class PollsService {

    private final JdbcClient db;

    public PollsService(JdbcClient db) {
        this.db = db;
    }

    @Transactional
    public Map<String, Object> create(
            String sellerId, String kitchenId, String question, List<String> options, String closesAt) {
        requireOwnedKitchen(sellerId, kitchenId);
        if (options == null || options.size() < 2) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "NEED_TWO_OPTIONS");
        }
        String id = UUID.randomUUID().toString();
        db.sql("""
                INSERT INTO "Poll" (id, "kitchenId", question, options, "closesAt")
                VALUES (:id, :kitchenId, :question, :options, :closesAt::timestamp)
                """)
                .param("id", id)
                .param("kitchenId", kitchenId)
                .param("question", question)
                .param("options", options.toArray(String[]::new))
                .param("closesAt", closesAt == null || closesAt.isBlank() ? null : closesAt)
                .update();
        return get(id, null);
    }

    /** Public list for a kitchen, newest first, with tallies (and the caller's vote if any). */
    public List<Map<String, Object>> listForKitchen(String kitchenId, String buyerId) {
        List<String> ids = db.sql("""
                SELECT id FROM "Poll" WHERE "kitchenId" = :kitchenId ORDER BY "createdAt" DESC LIMIT 20
                """)
                .param("kitchenId", kitchenId)
                .query(String.class)
                .list();
        return ids.stream().map(id -> get(id, buyerId)).toList();
    }

    @Transactional
    public Map<String, Object> vote(String buyerId, String pollId, int optionIndex) {
        record PollRow(String id, int optionCount, boolean closed) {
        }
        PollRow poll = db.sql("""
                SELECT id, cardinality(options) AS option_count,
                       ("closesAt" IS NOT NULL AND "closesAt" < now()) AS closed
                FROM "Poll" WHERE id = :id
                """)
                .param("id", pollId)
                .query((rs, n) -> new PollRow(
                        rs.getString("id"), rs.getInt("option_count"), rs.getBoolean("closed")))
                .optional()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "POLL_NOT_FOUND"));
        if (poll.closed()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "POLL_CLOSED");
        }
        if (optionIndex < 0 || optionIndex >= poll.optionCount()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "OPTION_OUT_OF_RANGE");
        }
        try {
            db.sql("""
                    INSERT INTO "PollVote" (id, "pollId", "buyerId", "optionIndex")
                    VALUES (:id, :pollId, :buyerId, :optionIndex)
                    """)
                    .param("id", UUID.randomUUID().toString())
                    .param("pollId", pollId)
                    .param("buyerId", buyerId)
                    .param("optionIndex", optionIndex)
                    .update();
        } catch (DuplicateKeyException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "ALREADY_VOTED"); // FR17
        }
        return get(pollId, buyerId);
    }

    @Transactional
    public Map<String, Object> close(String sellerId, String pollId) {
        String kitchenId = db.sql("SELECT \"kitchenId\" FROM \"Poll\" WHERE id = :id")
                .param("id", pollId)
                .query(String.class)
                .optional()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "POLL_NOT_FOUND"));
        requireOwnedKitchen(sellerId, kitchenId);
        db.sql("UPDATE \"Poll\" SET \"closesAt\" = now() WHERE id = :id AND (\"closesAt\" IS NULL OR \"closesAt\" > now())")
                .param("id", pollId)
                .update();
        return get(pollId, null);
    }

    /** One poll with option tallies; buyerId (nullable) resolves the caller's own vote. */
    public Map<String, Object> get(String pollId, String buyerId) {
        record PollRow(String id, String kitchenId, String question, String[] options,
                java.sql.Timestamp closesAt, java.sql.Timestamp createdAt) {
        }
        PollRow poll = db.sql("""
                SELECT id, "kitchenId", question, options, "closesAt", "createdAt"
                FROM "Poll" WHERE id = :id
                """)
                .param("id", pollId)
                .query((rs, n) -> new PollRow(
                        rs.getString("id"), rs.getString("kitchenId"), rs.getString("question"),
                        (String[]) rs.getArray("options").getArray(),
                        rs.getTimestamp("closesAt"), rs.getTimestamp("createdAt")))
                .optional()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "POLL_NOT_FOUND"));

        int[] tallies = new int[poll.options().length];
        db.sql("SELECT \"optionIndex\", count(*)::int AS c FROM \"PollVote\" WHERE \"pollId\" = :id GROUP BY \"optionIndex\"")
                .param("id", pollId)
                .query((rs, n) -> Map.entry(rs.getInt("optionIndex"), rs.getInt("c")))
                .list()
                .forEach(e -> {
                    if (e.getKey() >= 0 && e.getKey() < tallies.length) {
                        tallies[e.getKey()] = e.getValue();
                    }
                });

        Integer myVote = buyerId == null ? null
                : db.sql("SELECT \"optionIndex\" FROM \"PollVote\" WHERE \"pollId\" = :id AND \"buyerId\" = :buyerId")
                        .param("id", pollId)
                        .param("buyerId", buyerId)
                        .query(Integer.class)
                        .optional()
                        .orElse(null);

        int total = 0;
        for (int t : tallies) {
            total += t;
        }

        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", poll.id());
        row.put("kitchenId", poll.kitchenId());
        row.put("question", poll.question());
        row.put("options", poll.options());
        row.put("tallies", tallies);
        row.put("totalVotes", total);
        row.put("closesAt", poll.closesAt() == null ? null : poll.closesAt().toLocalDateTime());
        row.put("closed", poll.closesAt() != null && poll.closesAt().toInstant().isBefore(java.time.Instant.now()));
        row.put("createdAt", poll.createdAt().toLocalDateTime());
        row.put("myVote", myVote);
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
