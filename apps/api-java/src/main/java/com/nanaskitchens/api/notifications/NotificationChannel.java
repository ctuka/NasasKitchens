package com.nanaskitchens.api.notifications;

/**
 * Story 4.4 — external fan-out (architecture.md: FCM push + SES email) behind the same
 * provider pattern as delivery/payments. The in-app inbox row is the source of truth and
 * is written regardless of channel; this only carries the "buzz the user's device" part,
 * so implementations may drop messages without data loss.
 */
public interface NotificationChannel {

    /** log | fcm | ses */
    String name();

    void send(String userId, String type, String title, String body);
}
