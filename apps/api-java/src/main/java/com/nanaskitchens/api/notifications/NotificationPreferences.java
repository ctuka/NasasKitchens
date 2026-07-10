package com.nanaskitchens.api.notifications;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Story 4.4 follow-up (FR22) — maps a notification type to a user-facing category and
 * decides whether an external channel is allowed for a user. The in-app inbox ("log") is
 * never gated; only email and push (webhook) respect the opt-outs.
 */
public final class NotificationPreferences {

    private NotificationPreferences() {
    }

    /** Categories shown in the settings grid; every notification type maps to one. */
    public static final List<String> CATEGORIES = List.of("orders", "community", "trust");

    /** External channels a user can toggle (UI label → dispatcher channel name). */
    public static final Map<String, String> CHANNELS = Map.of("email", "email", "push", "webhook");

    private static final Set<String> COMMUNITY_TYPES = Set.of(
            "review_received", "dish_request", "dish_request_accepted", "dish_request_declined");
    private static final Set<String> TRUST_TYPES = Set.of("score_dispute_resolved");

    public static String categoryOf(String type) {
        if (COMMUNITY_TYPES.contains(type)) {
            return "community";
        }
        if (TRUST_TYPES.contains(type)) {
            return "trust";
        }
        return "orders"; // order_* and payment_* lifecycle events
    }

    /** UI channel key ("email"/"push") for a dispatcher channel name, or null if not toggleable. */
    public static String prefKeyForChannel(String channelName) {
        return switch (channelName) {
            case "email" -> "email";
            case "webhook" -> "push";
            default -> null; // "log" and anything else is always allowed
        };
    }

    /** True when the channel may fire for this type given the user's disabled set. */
    public static boolean allows(Set<String> disabled, String channelName, String type) {
        String prefKey = prefKeyForChannel(channelName);
        if (prefKey == null) {
            return true;
        }
        return !disabled.contains(categoryOf(type) + ":" + prefKey);
    }
}
