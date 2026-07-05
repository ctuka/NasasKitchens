package com.nanaskitchens.api.orders;

import com.nanaskitchens.api.inventory.InventoryService;
import com.nanaskitchens.api.kitchens.AddressCrypto;
import com.nanaskitchens.api.kitchens.dto.MenuDayResponse;
import com.nanaskitchens.api.orders.dto.CreateOrderRequest;
import com.nanaskitchens.api.orders.dto.OrderDetailResponse;
import com.nanaskitchens.api.orders.dto.OrderSummary;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.json.JsonMapper;

/** Ported from apps/api/src/orders (Story 3.3 core, Stripe integration remains Story 3.4). */
@Service
public class OrdersService {

    private static final double COMMISSION_RATE = 0.15;
    private static final Set<String> FINAL_STATUSES = Set.of("completed", "cancelled");

    private final JdbcClient db;
    private final InventoryService inventory;
    private final AddressCrypto addressCrypto;
    private final JsonMapper jsonMapper;

    public OrdersService(
            JdbcClient db, InventoryService inventory, AddressCrypto addressCrypto, JsonMapper jsonMapper) {
        this.db = db;
        this.inventory = inventory;
        this.addressCrypto = addressCrypto;
        this.jsonMapper = jsonMapper;
    }

    /**
     * FR15 (server-side): without confirm=true no order is created — only a priced summary is
     * returned. Agent/MCP and UI all go through this same path (NFR3).
     * Anti-hallucination: every menuItemId must be on the kitchen's PUBLISHED menu for that day.
     */
    @Transactional
    public Map<String, Object> place(String buyerId, CreateOrderRequest input) {
        record MenuRow(String menuItemId, String dishName, int priceCents) {
        }
        List<MenuRow> menuRows = db.sql("""
                SELECT mi.id AS menu_item_id, d.name AS dish_name, d."priceCents"
                FROM "MenuDay" md
                JOIN "MenuItem" mi ON mi."menuDayId" = md.id
                JOIN "Dish" d ON d.id = mi."dishId"
                WHERE md.id = :menuDayId AND md."kitchenId" = :kitchenId AND md.status = 'published'
                """)
                .param("menuDayId", input.menuDayId())
                .param("kitchenId", input.kitchenId())
                .query((rs, n) -> new MenuRow(rs.getString("menu_item_id"), rs.getString("dish_name"),
                        rs.getInt("priceCents")))
                .list();
        if (menuRows.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "MENU_NOT_PUBLISHED");
        }

        Map<String, MenuRow> valid = new HashMap<>();
        for (MenuRow row : menuRows) {
            valid.put(row.menuItemId(), row);
        }
        for (CreateOrderRequest.Item item : input.items()) {
            if (!valid.containsKey(item.menuItemId())) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST, "ITEM_NOT_IN_MENU:" + item.menuItemId());
            }
            if (item.qty() < 1) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "QTY_INVALID");
            }
        }

        int totalCents = input.items().stream()
                .mapToInt(it -> valid.get(it.menuItemId()).priceCents() * it.qty())
                .sum();
        int commissionCents = (int) Math.round(totalCents * COMMISSION_RATE);

        OrderSummary summary = new OrderSummary(
                input.kitchenId(),
                input.items().stream()
                        .map(it -> new OrderSummary.Item(
                                it.menuItemId(),
                                valid.get(it.menuItemId()).dishName(),
                                it.qty(),
                                valid.get(it.menuItemId()).priceCents()))
                        .toList(),
                input.readySlot(),
                input.fulfillment(),
                totalCents);

        if (!input.confirm()) {
            return Map.of("confirmed", false, "summary", summary); // FR15: summary first, no side effects
        }

        // Atomic: decrement + order in one transaction (Story 2.3 / architecture Workflow 1)
        String orderId = UUID.randomUUID().toString();
        for (CreateOrderRequest.Item item : input.items()) {
            inventory.decrement(item.menuItemId(), item.qty());
        }
        db.sql("""
                INSERT INTO "Order"
                  (id, "buyerId", "kitchenId", "menuDayId", status, "readySlot", fulfillment,
                   "totalCents", "commissionCents", "idempotencyKey")
                VALUES
                  (:id, :buyerId, :kitchenId, :menuDayId, 'confirmed', :readySlot, :fulfillment,
                   :totalCents, :commissionCents, :idempotencyKey)
                """)
                .param("id", orderId)
                .param("buyerId", buyerId)
                .param("kitchenId", input.kitchenId())
                .param("menuDayId", input.menuDayId())
                .param("readySlot", parseReadySlot(input.readySlot()))
                .param("fulfillment", input.fulfillment())
                .param("totalCents", totalCents)
                .param("commissionCents", commissionCents)
                // becomes the Stripe draft id in Story 3.4
                .param("idempotencyKey", UUID.randomUUID().toString())
                .update();
        for (CreateOrderRequest.Item item : input.items()) {
            db.sql("""
                    INSERT INTO "OrderItem" (id, "orderId", "menuItemId", qty, "unitPriceCents")
                    VALUES (:id, :orderId, :menuItemId, :qty, :unitPriceCents)
                    """)
                    .param("id", UUID.randomUUID().toString())
                    .param("orderId", orderId)
                    .param("menuItemId", item.menuItemId())
                    .param("qty", item.qty())
                    .param("unitPriceCents", valid.get(item.menuItemId()).priceCents())
                    .update();
        }
        db.sql("""
                INSERT INTO "AuditLog" (id, actor, entity, action, "after")
                VALUES (:id, :actor, :entity, 'create', :after::jsonb)
                """)
                .param("id", UUID.randomUUID().toString())
                .param("actor", buyerId)
                .param("entity", "Order:" + orderId)
                .param("after", jsonMapper.writeValueAsString(summary))
                .update();

        return Map.of("confirmed", true, "order", detail(buyerId, orderId));
    }

    /** FR10: step-by-step address disclosure — street address only on a confirmed pickup order. */
    public OrderDetailResponse detail(String buyerId, String orderId) {
        record OrderRow(String id, String buyerId, String kitchenId, String menuDayId, String status,
                LocalDateTime readySlot, String fulfillment, int totalCents, int commissionCents,
                String paymentIntentId, String idempotencyKey, LocalDateTime createdAt,
                String kitchenName, String addressEncrypted) {
        }
        OrderRow order = db.sql("""
                SELECT o.*, k.name AS kitchen_name, k."addressEncrypted"
                FROM "Order" o JOIN "Kitchen" k ON k.id = o."kitchenId"
                WHERE o.id = :id
                """)
                .param("id", orderId)
                .query((rs, n) -> new OrderRow(
                        rs.getString("id"), rs.getString("buyerId"), rs.getString("kitchenId"),
                        rs.getString("menuDayId"), rs.getString("status"),
                        rs.getTimestamp("readySlot").toLocalDateTime(), rs.getString("fulfillment"),
                        rs.getInt("totalCents"), rs.getInt("commissionCents"),
                        rs.getString("paymentIntentId"), rs.getString("idempotencyKey"),
                        rs.getTimestamp("createdAt").toLocalDateTime(),
                        rs.getString("kitchen_name"), rs.getString("addressEncrypted")))
                .optional()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (!order.buyerId().equals(buyerId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN); // MCP AC4: buyer scope
        }

        List<OrderDetailResponse.Item> items = db.sql("""
                SELECT oi.id, oi."orderId", oi."menuItemId", oi.qty, oi."unitPriceCents",
                       mi."menuDayId", mi."dishId", mi."portionsTotal", mi."portionsRemaining",
                       d."kitchenId" AS dish_kitchen_id, d.name, d.description, d.photo,
                       d."priceCents", d."dietaryTags"
                FROM "OrderItem" oi
                JOIN "MenuItem" mi ON mi.id = oi."menuItemId"
                JOIN "Dish" d ON d.id = mi."dishId"
                WHERE oi."orderId" = :orderId
                """)
                .param("orderId", orderId)
                .query((rs, n) -> new OrderDetailResponse.Item(
                        rs.getString("id"),
                        rs.getString("orderId"),
                        rs.getString("menuItemId"),
                        rs.getInt("qty"),
                        rs.getInt("unitPriceCents"),
                        new OrderDetailResponse.MenuItemRef(
                                rs.getString("menuItemId"),
                                rs.getString("menuDayId"),
                                rs.getString("dishId"),
                                rs.getInt("portionsTotal"),
                                rs.getInt("portionsRemaining"),
                                new MenuDayResponse.Dish(
                                        rs.getString("dishId"),
                                        rs.getString("dish_kitchen_id"),
                                        rs.getString("name"),
                                        rs.getString("description"),
                                        rs.getString("photo"),
                                        rs.getInt("priceCents"),
                                        stringList(rs, "dietaryTags")))))
                .list();

        boolean discloseAddress = "pickup".equals(order.fulfillment())
                && !"pending".equals(order.status())
                && !"cancelled".equals(order.status());
        return new OrderDetailResponse(
                order.id(), order.buyerId(), order.kitchenId(), order.menuDayId(), order.status(),
                order.readySlot(), order.fulfillment(), order.totalCents(), order.commissionCents(),
                order.paymentIntentId(), order.idempotencyKey(), order.createdAt(), items,
                order.kitchenName(),
                discloseAddress ? addressCrypto.decrypt(order.addressEncrypted()) : null);
    }

    @Transactional
    public Map<String, Object> cancel(String buyerId, String orderId) {
        Map<String, Object> order = db.sql("SELECT * FROM \"Order\" WHERE id = :id FOR UPDATE")
                .param("id", orderId)
                .query((rs, n) -> orderRowAsMap(rs))
                .optional()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (!buyerId.equals(order.get("buyerId"))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
        if (FINAL_STATUSES.contains((String) order.get("status"))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "NOT_CANCELLABLE");
        }
        record ItemQty(String menuItemId, int qty) {
        }
        List<ItemQty> items = db.sql("SELECT \"menuItemId\", qty FROM \"OrderItem\" WHERE \"orderId\" = :id")
                .param("id", orderId)
                .query((rs, n) -> new ItemQty(rs.getString("menuItemId"), rs.getInt("qty")))
                .list();
        for (ItemQty item : items) {
            inventory.restore(item.menuItemId(), item.qty()); // refund in the same transaction (AC2)
        }
        db.sql("UPDATE \"Order\" SET status = 'cancelled' WHERE id = :id").param("id", orderId).update();
        order.put("status", "cancelled");
        return order;
    }

    public OrderDetailResponse status(String buyerId, String orderId) {
        return detail(buyerId, orderId);
    }

    private static Map<String, Object> orderRowAsMap(ResultSet rs) throws SQLException {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", rs.getString("id"));
        row.put("buyerId", rs.getString("buyerId"));
        row.put("kitchenId", rs.getString("kitchenId"));
        row.put("menuDayId", rs.getString("menuDayId"));
        row.put("status", rs.getString("status"));
        row.put("readySlot", rs.getTimestamp("readySlot").toLocalDateTime());
        row.put("fulfillment", rs.getString("fulfillment"));
        row.put("totalCents", rs.getInt("totalCents"));
        row.put("commissionCents", rs.getInt("commissionCents"));
        row.put("paymentIntentId", rs.getString("paymentIntentId"));
        row.put("idempotencyKey", rs.getString("idempotencyKey"));
        row.put("createdAt", rs.getTimestamp("createdAt").toLocalDateTime());
        return row;
    }

    /** Accepts ISO datetimes with or without offset, mirroring JS `new Date(string)`. */
    private static LocalDateTime parseReadySlot(String value) {
        try {
            return LocalDateTime.ofInstant(Instant.parse(value), ZoneOffset.UTC);
        } catch (DateTimeParseException e) {
            try {
                return LocalDateTime.parse(value);
            } catch (DateTimeParseException e2) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "READY_SLOT_INVALID");
            }
        }
    }

    private static List<String> stringList(ResultSet rs, String column) throws SQLException {
        java.sql.Array array = rs.getArray(column);
        return array == null ? List.of() : Arrays.asList((String[]) array.getArray());
    }
}
