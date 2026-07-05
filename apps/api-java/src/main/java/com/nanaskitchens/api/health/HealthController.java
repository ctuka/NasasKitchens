package com.nanaskitchens.api.health;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Map;
import javax.sql.DataSource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HealthController {

    private final DataSource dataSource;

    public HealthController(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @GetMapping("/health")
    public Map<String, String> health() {
        String db = "down";
        try (Connection connection = dataSource.getConnection()) {
            if (connection.isValid(2)) {
                db = "up";
            }
        } catch (SQLException ignored) {
            // db stays "down"
        }
        return Map.of("status", "ok", "db", db);
    }
}
