package com.nanaskitchens.api.notifications;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Always registered; the dispatcher only calls it when "log" is in
 * app.notifications.channels (the default). The web bell reads the inbox rows regardless.
 */
@Component
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
