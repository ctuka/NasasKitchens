package com.nanaskitchens.api.notifications;

import com.nanaskitchens.api.notifications.NotificationsService.NotificationCreated;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Fans a committed notification out to the external channel. AFTER_COMMIT so a rolled-back
 * order never buzzes anyone; fallbackExecution covers notify() calls outside a transaction.
 * Channel failures are logged and swallowed — the inbox row already exists.
 */
@Component
public class NotificationDispatcher {

    private static final Logger log = LoggerFactory.getLogger(NotificationDispatcher.class);

    private final NotificationChannel channel;

    public NotificationDispatcher(NotificationChannel channel) {
        this.channel = channel;
    }

    @TransactionalEventListener(fallbackExecution = true)
    public void onNotificationCreated(NotificationCreated event) {
        try {
            channel.send(event.userId(), event.type(), event.title(), event.body());
        } catch (RuntimeException e) {
            log.warn("notification channel {} failed for user {}: {}",
                    channel.name(), event.userId(), e.getMessage());
        }
    }
}
