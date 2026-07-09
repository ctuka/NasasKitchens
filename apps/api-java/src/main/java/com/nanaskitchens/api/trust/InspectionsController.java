package com.nanaskitchens.api.trust;

import com.nanaskitchens.api.storage.PhotoStorage;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

/** Story 7.2 (FR20) — inspection assignments, evidence photos and structured scoring. */
@RestController
@RequestMapping("/inspections")
public class InspectionsController {

    public record AssignRequest(
            @NotBlank String kitchenId,
            @NotBlank @Email String inspectorEmail,
            @NotBlank String scheduledAt) {
    }

    public record ScoreRequest(@NotNull Map<String, Integer> subScores, List<String> photos) {
    }

    private static final Set<String> IMAGE_TYPES = Set.of("image/jpeg", "image/png", "image/webp");

    private final InspectionsService inspections;
    private final PhotoStorage storage;

    public InspectionsController(InspectionsService inspections, PhotoStorage storage) {
        this.inspections = inspections;
        this.storage = storage;
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public Map<String, Object> assign(Authentication auth, @Valid @RequestBody AssignRequest request) {
        return inspections.assign(auth.getName(), request.kitchenId(), request.inspectorEmail(), request.scheduledAt());
    }

    @GetMapping("/assigned")
    @PreAuthorize("hasRole('INSPECTOR')")
    public List<Map<String, Object>> assigned(Authentication auth) {
        return inspections.assignedVisits(auth.getName());
    }

    /** AC2 photo evidence — upload before submit; URLs ride along in the score payload. */
    @PostMapping("/{visitId}/evidence")
    @PreAuthorize("hasRole('INSPECTOR')")
    public Map<String, String> evidence(
            Authentication auth, @PathVariable String visitId, @RequestParam("file") MultipartFile file)
            throws IOException {
        inspections.requireOwnVisit(auth.getName(), visitId);
        String contentType = file.getContentType();
        if (contentType == null || !IMAGE_TYPES.contains(contentType)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "UNSUPPORTED_PHOTO_TYPE");
        }
        if (file.getSize() == 0 || file.getSize() > 5 * 1024 * 1024) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "PHOTO_TOO_LARGE");
        }
        return Map.of("url", storage.store(file.getBytes(), contentType));
    }

    @PostMapping("/{visitId}/score")
    @PreAuthorize("hasRole('INSPECTOR')")
    public Map<String, Object> score(
            Authentication auth, @PathVariable String visitId, @Valid @RequestBody ScoreRequest request) {
        return inspections.submitScore(auth.getName(), visitId, request.subScores(), request.photos());
    }
}
