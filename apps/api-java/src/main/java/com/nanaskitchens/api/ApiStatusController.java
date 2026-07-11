package com.nanaskitchens.api;

import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Minimal unauthenticated status endpoints for local development and load-balancer checks. */
@RestController
public class ApiStatusController {

    @GetMapping("/")
    public Map<String, String> root() {
        return Map.of("service", "nanas-kitchens-api", "status", "ok", "health", "/health");
    }

}
