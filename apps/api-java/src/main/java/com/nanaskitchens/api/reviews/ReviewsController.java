package com.nanaskitchens.api.reviews;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Map;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** Story 6.1 (FR16) — post-completion ratings & reviews. */
@RestController
public class ReviewsController {

    public record CreateReviewRequest(
            @NotBlank String orderId,
            @NotNull @Min(1) @Max(5) Integer rating,
            @Size(max = 1000) String comment) {
    }

    private final ReviewsService reviews;

    public ReviewsController(ReviewsService reviews) {
        this.reviews = reviews;
    }

    @PostMapping("/kitchens/{kitchenId}/reviews")
    @PreAuthorize("hasRole('BUYER')")
    public Map<String, Object> create(
            Authentication auth, @PathVariable String kitchenId, @Valid @RequestBody CreateReviewRequest request) {
        return reviews.create(auth.getName(), kitchenId, request.orderId(), request.rating(), request.comment());
    }

    @GetMapping("/kitchens/{kitchenId}/reviews")
    public List<Map<String, Object>> list(@PathVariable String kitchenId) {
        return reviews.listForKitchen(kitchenId);
    }

    @GetMapping("/orders/{orderId}/review")
    public Map<String, Object> mine(Authentication auth, @PathVariable String orderId) {
        return reviews.get(orderId, auth.getName());
    }
}
