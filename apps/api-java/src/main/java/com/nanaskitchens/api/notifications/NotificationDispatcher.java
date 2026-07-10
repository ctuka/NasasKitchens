package com.nanaskitchens.api.notifications;

import com.nanaskitchens.api.notifications.NotificationsService.NotificationCreated;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Fans a committed notification out to every enabled external channel. AFTER_COMMIT so a
 * rolled-back order never buzzes anyone; fallbackExecution covers notify() calls outside a
 * transaction. Each channel's failure is logged and swallowed — the inbox row already
 * exists, so a down mail server never loses the notification (architecture NFR8).
 *
 * app.notifications.channels selects which of the registered channels actually fire; a
 * channel named in config but missing its bean (e.g. email without spring.mail.host) is
 * warned about once at startup and skipped.
 */
@Component
public class NotificationDispatcher {

    private static final Logger log = LoggerFactory.getLogger(NotificationDispatcher.class);

    private final List<NotificationChannel> enabled;

    public NotificationDispatcher(
            List<NotificationChannel> channels,
            @Value("${app.notifications.channels:log}") List<String> configured) {
        Set<String> want = configured.stream().map(String::trim).collect(Collectors.toSet());
        this.enabled = channels.stream().filter(c -> want.contains(c.name())).toList();
        Set<String> present = channels.stream().map(NotificationChannel::name).collect(Collectors.toSet());
        for (String name : want) {
            if (!present.contains(name)) {
                log.warn("notification channel '{}' is configured but has no bean — is it credential-gated?", name);
            }
        }
        log.info("notification channels enabled: {}",
                enabled.stream().map(NotificationChannel::name).toList());
    }

    @TransactionalEventListener(fallbackExecution = true)
    public void onNotificationCreated(NotificationCreated event) {
        for (NotificationChannel channel : enabled) {
            try {
                channel.send(event.userId(), event.type(), event.title(), event.body());
            } catch (RuntimeException e) {
                log.warn("notification channel {} failed for user {}: {}",
                        channel.name(), event.userId(), e.getMessage());
            }
        }
    }
}
