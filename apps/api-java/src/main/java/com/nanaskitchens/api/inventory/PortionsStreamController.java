package com.nanaskitchens.api.inventory;

import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

/**
 * Story 2.3 AC3 — public SSE feed of live portion counts for a kitchen's published menu
 * (kitchen profile pages update within 3 s; clients fall back to 10 s polling of
 * POST /inventory/remaining if SSE is unavailable).
 */
@RestController
public class PortionsStreamController {

    private final PortionsStreamService portionsStream;

    public PortionsStreamController(PortionsStreamService portionsStream) {
        this.portionsStream = portionsStream;
    }

    @GetMapping(value = "/kitchens/{kitchenId}/portions/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> stream(@PathVariable String kitchenId) {
        return portionsStream.stream(kitchenId);
    }
}
