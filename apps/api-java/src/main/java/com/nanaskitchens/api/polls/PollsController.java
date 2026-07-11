package com.nanaskitchens.api.polls;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
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

/** Story 6.2 (FR17) — menu polls with one vote per buyer. */
@RestController
public class PollsController {

    public record CreatePollRequest(
            @NotBlank @Size(max = 200) String question,
            @NotEmpty @Size(min = 2, max = 6) List<@NotBlank @Size(max = 100) String> options,
            String closesAt) {
    }

    public record VoteRequest(@NotNull Integer optionIndex) {
    }

    private final PollsService polls;

    public PollsController(PollsService polls) {
        this.polls = polls;
    }

    @PostMapping("/kitchens/{kitchenId}/polls")
    @PreAuthorize("hasRole('SELLER')")
    public Map<String, Object> create(
            Authentication auth, @PathVariable String kitchenId, @Valid @RequestBody CreatePollRequest request) {
        return polls.create(auth.getName(), kitchenId, request.question(), request.options(), request.closesAt());
    }

    @GetMapping("/kitchens/{kitchenId}/polls")
    public List<Map<String, Object>> list(Authentication auth, @PathVariable String kitchenId) {
        // Public endpoint: an anonymous caller has no vote to resolve.
        boolean authed = auth != null && auth.isAuthenticated()
                && !"anonymousUser".equals(auth.getPrincipal());
        return polls.listForKitchen(kitchenId, authed ? auth.getName() : null);
    }

    @PostMapping("/polls/{pollId}/vote")
    @PreAuthorize("hasRole('BUYER')")
    public Map<String, Object> vote(
            Authentication auth, @PathVariable String pollId, @Valid @RequestBody VoteRequest request) {
        return polls.vote(auth.getName(), pollId, request.optionIndex());
    }

    @PostMapping("/polls/{pollId}/close")
    @PreAuthorize("hasRole('SELLER')")
    public Map<String, Object> close(Authentication auth, @PathVariable String pollId) {
        return polls.close(auth.getName(), pollId);
    }
}
