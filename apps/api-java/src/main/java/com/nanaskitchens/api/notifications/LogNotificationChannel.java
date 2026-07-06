package com.nanaskitchens.api.notifications;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Stand-in until FCM/SES credentials exist — the web bell reads the inbox rows anyway. */
@Component
@ConditionalOnProperty(name = "app.notifications.channel", havingValue = "log", matchIfMissing = true)
public class LogNotificationChannel implements NotificationChannel {

    private static final Logger log = LoggerFactory.getLogger(LogNotificationChannel.class);

    @Override
    public String name() {
        return "log";
    }

    @Override
    public void send(String userId, String type, String title, String body) {
        log.info("notification user={} type={} title=\"{}\"", userId, type, title);
    }
}
