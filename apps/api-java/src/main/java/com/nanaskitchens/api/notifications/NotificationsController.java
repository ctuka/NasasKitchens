package com.nanaskitchens.api.notifications;

import java.util.List;
import java.util.Map;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/notifications")
public class NotificationsController {

    public record PreferencesRequest(List<String> disabled) {
    }

    private final NotificationsService notifications;
    private final NotificationPreferencesService preferences;

    public NotificationsController(
            NotificationsService notifications, NotificationPreferencesService preferences) {
        this.notifications = notifications;
        this.preferences = preferences;
    }

    @GetMapping
    public Map<String, Object> list(Authentication auth) {
        return notifications.list(auth.getName());
    }

    @PostMapping("/read")
    public Map<String, Object> markAllRead(Authentication auth) {
        return notifications.markAllRead(auth.getName());
    }

    /** Story 4.4 follow-up — the email/push opt-out grid for the settings page. */
    @GetMapping("/preferences")
    public Map<String, Object> getPreferences(Authentication auth) {
        return preferences.grid(auth.getName());
    }

    @PutMapping("/preferences")
    public Map<String, Object> updatePreferences(Authentication auth, @RequestBody PreferencesRequest request) {
        return preferences.update(auth.getName(), request.disabled());
    }
}
