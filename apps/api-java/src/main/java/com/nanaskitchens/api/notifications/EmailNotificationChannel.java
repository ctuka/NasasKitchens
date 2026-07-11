package com.nanaskitchens.api.notifications;

import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

/**
 * Story 4.4 external fan-out — email via SMTP (works against SES, Mailgun, Postfix, or a
 * local dev sink like Mailpit). Registered only when spring.mail.host is set; otherwise
 * the dispatcher warns that a configured "email" channel has no bean. The user's address
 * comes from the platform DB, so no address is stored on the notification itself.
 */
@Component
@ConditionalOnProperty(name = "spring.mail.host")
public class EmailNotificationChannel implements NotificationChannel {

    private static final Logger log = LoggerFactory.getLogger(EmailNotificationChannel.class);

    private final JavaMailSender mailSender;
    private final JdbcClient db;
    private final String from;

    public EmailNotificationChannel(
            JavaMailSender mailSender,
            JdbcClient db,
            @Value("${app.notifications.email.from:no-reply@nanaskitchens.com}") String from) {
        this.mailSender = mailSender;
        this.db = db;
        this.from = from;
    }

    @Override
    public String name() {
        return "email";
    }

    @Override
    public void send(String userId, String type, String title, String body) {
        Optional<String> email = db.sql("SELECT email FROM \"User\" WHERE id = :id")
                .param("id", userId)
                .query(String.class)
                .optional();
        if (email.isEmpty()) {
            return; // deleted user or system actor — nothing to email
        }
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(email.get());
        message.setSubject(title);
        message.setText(body + "\n\n— Nanas' Kitchens\nManage notifications in the app.");
        mailSender.send(message); // failures bubble to the dispatcher, which logs + swallows
        log.debug("emailed {} notification to {}", type, email.get());
    }
}
