package com.nanaskitchens.api.admin;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Story 7.3 — admin console: inspectors, visit overview, score-dispute queue. */
@RestController
@RequestMapping("/admin")
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {

    public record InviteInspectorRequest(
            @NotBlank @Email String email, @NotBlank @Size(min = 8) String password) {
    }

    public record ResolveDisputeRequest(
            @Pattern(regexp = "dismissed|annulled") String resolution, @Size(max = 500) String note) {
    }

    private final AdminService admin;

    public AdminController(AdminService admin) {
        this.admin = admin;
    }

    @GetMapping("/inspectors")
    public List<Map<String, Object>> inspectors() {
        return admin.listInspectors();
    }

    @PostMapping("/inspectors")
    public Map<String, Object> invite(Authentication auth, @Valid @RequestBody InviteInspectorRequest request) {
        return admin.inviteInspector(auth.getName(), request.email(), request.password());
    }

    @GetMapping("/inspections")
    public List<Map<String, Object>> visits() {
        return admin.listVisits();
    }

    @GetMapping("/kitchens")
    public List<Map<String, Object>> kitchens() {
        return admin.listKitchens();
    }

    @GetMapping("/disputes")
    public List<Map<String, Object>> disputes(@RequestParam(required = false) String status) {
        return admin.listDisputes(status);
    }

    @PostMapping("/disputes/{id}/resolve")
    public Map<String, Object> resolve(
            Authentication auth, @PathVariable String id, @Valid @RequestBody ResolveDisputeRequest request) {
        return admin.resolveDispute(auth.getName(), id, request.resolution(), request.note());
    }
}
