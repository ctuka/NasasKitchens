package com.nanaskitchens.api.orders;

import com.nanaskitchens.api.delivery.DeliveryService;
import com.nanaskitchens.api.inventory.InventoryService;
import com.nanaskitchens.api.kitchens.AddressCrypto;
import com.nanaskitchens.api.payments.PaymentsService;
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
    private final PaymentsService payments;
    private final DeliveryService delivery;

    public OrdersService(
            JdbcClient db, InventoryService inventory, AddressCrypto addressCrypto, JsonMapper jsonMapper,
            PaymentsService payments, DeliveryService delivery) {
        this.db = db;
        this.inventory = inventory;
        this.addressCrypto = addressCrypto;
        this.jsonMapper = jsonMapper;
        this.payments = payments;
        this.delivery = delivery;
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

        // FR9/NFR5: delivery needs a drop-off address (validated up front so the agent can
        // ask the buyer before any inventory or payment side effects happen).
        boolean isDelivery = "delivery".equals(input.fulfillment());
        String deliveryAddress =
                input.deliveryAddress() == null || input.deliveryAddress().isBlank()
                        ? null
                        : input.deliveryAddress().trim();
        if (isDelivery && deliveryAddress == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "ADDRESS_REQUIRED: delivery orders need deliveryAddress (street, city)");
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
                isDelivery ? deliveryAddress : null,
                totalCents);

        if (!input.confirm()) {
            return Map.of("confirmed", false, "summary", summary); // FR15: summary first, no side effects
        }

        // Atomic: decrement + order in one transaction (Story 2.3 / architecture Workflow 1)
        String orderId = UUID.randomUUID().toString();
        String idempotencyKey = UUID.randomUUID().toString(); // doubles as Stripe idempotency key (Story 3.4)
        for (CreateOrderRequest.Item item : input.items()) {
            inventory.decrement(item.menuItemId(), item.qty());
        }
        db.sql("""
                INSERT INTO "Order"
                  (id, "buyerId", "kitchenId", "menuDayId", status, "readySlot", fulfillment,
                   "totalCents", "commissionCents", "idempotencyKey", "deliveryAddressEncrypted")
                VALUES
                  (:id, :buyerId, :kitchenId, :menuDayId, 'confirmed', :readySlot, :fulfillment,
                   :totalCents, :commissionCents, :idempotencyKey, :deliveryAddressEncrypted)
                """)
                .param("id", orderId)
                .param("buyerId", buyerId)
                .param("kitchenId", input.kitchenId())
                .param("menuDayId", input.menuDayId())
                .param("readySlot", parseReadySlot(input.readySlot()))
                .param("fulfillment", input.fulfillment())
                .param("totalCents", totalCents)
                .param("commissionCents", commissionCents)
                .param("idempotencyKey", idempotencyKey)
                .param("deliveryAddressEncrypted",
                        deliveryAddress == null ? null : addressCrypto.encrypt(deliveryAddress))
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
        // Story 3.4: charge inside the transaction — a failed payment rolls everything back.
        String paymentIntentId = payments.charge(totalCents, idempotencyKey, orderId);
        if (paymentIntentId != null) {
            db.sql("UPDATE \"Order\" SET \"paymentIntentId\" = :pi WHERE id = :id")
                    .param("pi", paymentIntentId)
                    .param("id", orderId)
                    .update();
        }

        // Story 4.2 (dev slice): delivery orders get a courier job right away.
        if ("delivery".equals(input.fulfillment())) {
            delivery.createJob(orderId);
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
                String kitchenName, String addressEncrypted,
                String deliveryProvider, String deliveryStatus, String trackingUrl, Integer deliveryFeeCents) {
        }
        OrderRow order = db.sql("""
                SELECT o.*, k.name AS kitchen_name, k."addressEncrypted",
                       dj.provider::text AS delivery_provider, dj.status AS delivery_status,
                       dj."trackingUrl", dj."feeCents" AS delivery_fee_cents
                FROM "Order" o
                JOIN "Kitchen" k ON k.id = o."kitchenId"
                LEFT JOIN "DeliveryJob" dj ON dj."orderId" = o.id
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
                        rs.getString("kitchen_name"), rs.getString("addressEncrypted"),
                        rs.getString("delivery_provider"), rs.getString("delivery_status"),
                        rs.getString("trackingUrl"), rs.getObject("delivery_fee_cents", Integer.class)))
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
                discloseAddress ? addressCrypto.decrypt(order.addressEncrypted()) : null,
                order.deliveryStatus() == null
                        ? null
                        : new OrderDetailResponse.Delivery(
                                order.deliveryProvider(), order.deliveryStatus(),
                                order.trackingUrl(), order.deliveryFeeCents()));
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

    /**
     * Accepts ISO datetimes with or without offset (mirroring JS `new Date(string)`), plus the
     * human formats the chat agent produces: "17:00" or a slot range like "17:00 - 17:30"
     * (start of the range wins, interpreted as today UTC — menus are per-day).
     */
    private static LocalDateTime parseReadySlot(String value) {
        String v = value.trim();
        try {
            return LocalDateTime.ofInstant(Instant.parse(v), ZoneOffset.UTC);
        } catch (DateTimeParseException ignored) {
        }
        try {
            return LocalDateTime.parse(v);
        } catch (DateTimeParseException ignored) {
        }
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("^(\\d{1,2}):(\\d{2})").matcher(v);
        if (m.find()) {
            int hour = Integer.parseInt(m.group(1));
            int minute = Integer.parseInt(m.group(2));
            if (hour <= 23 && minute <= 59) {
                return java.time.LocalDate.now(ZoneOffset.UTC).atTime(hour, minute);
            }
        }
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "READY_SLOT_INVALID: use ISO datetime (2026-07-10T17:00:00Z) or HH:mm");
    }

    private static List<String> stringList(ResultSet rs, String column) throws SQLException {
        java.sql.Array array = rs.getArray(column);
        return array == null ? List.of() : Arrays.asList((String[]) array.getArray());
    }
}
