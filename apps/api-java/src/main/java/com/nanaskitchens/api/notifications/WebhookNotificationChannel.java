package com.nanaskitchens.api.notifications;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import tools.jackson.databind.json.JsonMapper;

/**
 * Story 4.4 external fan-out — "push" via an HTTP relay. POSTs the notification as JSON to
 * app.notifications.webhook.url; a real deployment points that at an FCM/APNs relay (or n8n
 * / Knock / a Lambda) that owns the device-token registry. This keeps the platform free of
 * per-device credentials while still delivering a real out-of-band push (verifiable against
 * a local HTTP sink). Registered only when the webhook URL is configured.
 */
@Component
@ConditionalOnProperty(name = "app.notifications.webhook.url")
public class WebhookNotificationChannel implements NotificationChannel {

    private static final Logger log = LoggerFactory.getLogger(WebhookNotificationChannel.class);

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
    private final JsonMapper jsonMapper;
    private final String url;
    private final String secret;

    public WebhookNotificationChannel(
            JsonMapper jsonMapper,
            @Value("${app.notifications.webhook.url}") String url,
            @Value("${app.notifications.webhook.secret:}") String secret) {
        this.jsonMapper = jsonMapper;
        this.url = url;
        this.secret = secret;
    }

    @Override
    public String name() {
        return "webhook";
    }

    @Override
    public void send(String userId, String type, String title, String body) {
        String payload = jsonMapper.writeValueAsString(
                Map.of("userId", userId, "type", type, "title", title, "body", body));
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(5))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(payload));
        if (!secret.isBlank()) {
            request.header("X-Webhook-Secret", secret); // relay verifies this before pushing
        }
        try {
            HttpResponse<Void> response = http.send(request.build(), HttpResponse.BodyHandlers.discarding());
            if (response.statusCode() / 100 != 2) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "relay HTTP " + response.statusCode());
            }
            log.debug("pushed {} notification for user {} to relay", type, userId);
        } catch (java.io.IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "PUSH_RELAY_UNREACHABLE");
        }
    }
}
