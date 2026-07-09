package com.nanaskitchens.api.kitchens;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Story 1.3 (AC1/AC4): resolves a street address to a lat/lng via OpenStreetMap Nominatim.
 * Best-effort — any failure (network down, no result, timeout) yields an empty Optional so
 * the caller can fall back to manual coordinates instead of blocking onboarding.
 */
@Service
public class GeocodingService {

    private static final Logger log = LoggerFactory.getLogger(GeocodingService.class);

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(3))
            .build();
    private final JsonMapper jsonMapper;
    private final String baseUrl;
    private final String userAgent;

    public GeocodingService(
            JsonMapper jsonMapper,
            @Value("${app.geocoding.base-url:https://nominatim.openstreetmap.org}") String baseUrl,
            @Value("${app.geocoding.user-agent:NanasKitchens/1.0 (dev)}") String userAgent) {
        this.jsonMapper = jsonMapper;
        this.baseUrl = baseUrl;
        this.userAgent = userAgent;
    }

    /** Returns {lat, lng} for the address, or empty when it can't be resolved. */
    public Optional<double[]> geocode(String address) {
        if (address == null || address.isBlank()) {
            return Optional.empty();
        }
        try {
            String url = baseUrl + "/search?format=json&limit=1&q="
                    + URLEncoder.encode(address, StandardCharsets.UTF_8);
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(4))
                    .header("User-Agent", userAgent) // Nominatim usage policy requires this
                    .GET()
                    .build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                log.warn("Geocoding HTTP {} for address", response.statusCode());
                return Optional.empty();
            }
            JsonNode results = jsonMapper.readTree(response.body());
            if (!results.isArray() || results.isEmpty()) {
                return Optional.empty();
            }
            JsonNode first = results.get(0);
            double lat = Double.parseDouble(first.get("lat").asString());
            double lng = Double.parseDouble(first.get("lon").asString());
            return Optional.of(new double[] {lat, lng});
        } catch (Exception e) {
            log.warn("Geocoding failed: {}", e.getMessage());
            return Optional.empty();
        }
    }
}
