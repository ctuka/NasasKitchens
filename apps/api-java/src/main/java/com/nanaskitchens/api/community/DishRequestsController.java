package com.nanaskitchens.api.community;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Map;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Story 6.3 (FR18) — dish requests / cuisine offers with a seller accept flow. */
@RestController
public class DishRequestsController {

    public record CreateRequest(@NotBlank @Size(min = 3, max = 500) String text) {
    }

    public record RespondRequest(
            @Pattern(regexp = "accepted|declined") String status, @Size(max = 500) String note) {
    }

    private final DishRequestsService requests;

    public DishRequestsController(DishRequestsService requests) {
        this.requests = requests;
    }

    @PostMapping("/kitchens/{kitchenId}/dish-requests")
    @PreAuthorize("hasRole('BUYER')")
    public Map<String, Object> create(
            Authentication auth, @PathVariable String kitchenId, @Valid @RequestBody CreateRequest body) {
        return requests.create(auth.getName(), kitchenId, body.text());
    }

    @GetMapping("/kitchens/{kitchenId}/dish-requests")
    @PreAuthorize("hasRole('SELLER')")
    public List<Map<String, Object>> listForKitchen(
            Authentication auth, @PathVariable String kitchenId, @RequestParam(required = false) String status) {
        return requests.listForKitchen(auth.getName(), kitchenId, status);
    }

    @GetMapping("/dish-requests/mine")
    @PreAuthorize("hasRole('BUYER')")
    public List<Map<String, Object>> mine(Authentication auth) {
        return requests.listMine(auth.getName());
    }

    @PostMapping("/dish-requests/{id}/respond")
    @PreAuthorize("hasRole('SELLER')")
    public Map<String, Object> respond(
            Authentication auth, @PathVariable String id, @Valid @RequestBody RespondRequest body) {
        return requests.respond(auth.getName(), id, body.status(), body.note());
    }
}
