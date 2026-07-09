package com.nanaskitchens.api.admin;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.Map;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** Story 7.3 — the seller-facing half of the dispute flow (Story 7.2 AC3). */
@RestController
public class ScoreDisputesController {

    public record OpenDisputeRequest(@NotBlank @Size(min = 10, max = 1000) String reason) {
    }

    private final AdminService admin;

    public ScoreDisputesController(AdminService admin) {
        this.admin = admin;
    }

    @PostMapping("/kitchens/{kitchenId}/score-dispute")
    @PreAuthorize("hasRole('SELLER')")
    public Map<String, Object> open(
            Authentication auth, @PathVariable String kitchenId, @Valid @RequestBody OpenDisputeRequest request) {
        return admin.openDispute(auth.getName(), kitchenId, request.reason());
    }
}
