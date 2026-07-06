package com.nanaskitchens.api.notifications;

import java.util.Map;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/notifications")
public class NotificationsController {

    private final NotificationsService notifications;

    public NotificationsController(NotificationsService notifications) {
        this.notifications = notifications;
    }

    @GetMapping
    public Map<String, Object> list(Authentication auth) {
        return notifications.list(auth.getName());
    }

    @PostMapping("/read")
    public Map<String, Object> markAllRead(Authentication auth) {
        return notifications.markAllRead(auth.getName());
    }
}
