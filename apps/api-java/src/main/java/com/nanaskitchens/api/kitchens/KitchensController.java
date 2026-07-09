package com.nanaskitchens.api.kitchens;

import com.nanaskitchens.api.kitchens.dto.CreateKitchenRequest;
import com.nanaskitchens.api.kitchens.dto.KitchenProfile;
import com.nanaskitchens.api.kitchens.dto.KitchenSearchResult;
import com.nanaskitchens.api.kitchens.dto.MenuDayResponse;
import com.nanaskitchens.api.kitchens.dto.UpdateKitchenRequest;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/kitchens")
public class KitchensController {

    private final KitchensService kitchens;

    public KitchensController(KitchensService kitchens) {
        this.kitchens = kitchens;
    }

    @PostMapping
    @PreAuthorize("hasRole('SELLER')")
    public KitchenProfile create(Authentication auth, @Valid @RequestBody CreateKitchenRequest request) {
        return kitchens.create(auth.getName(), request);
    }

    @GetMapping("/search")
    public List<KitchenSearchResult> search(
            @RequestParam double lat,
            @RequestParam double lng,
            @RequestParam(required = false) String cuisine) {
        return kitchens.search(lat, lng, cuisine);
    }

    /** Seller portal entry point — resolves the caller's own kitchen (exact path beats {id}). */
    @GetMapping("/mine")
    @PreAuthorize("hasRole('SELLER')")
    public KitchenProfile mine(Authentication auth) {
        return kitchens.sellerKitchen(auth.getName());
    }

    /** Story 1.3 — profile edit (own kitchen only; a new address re-geocodes). */
    @PatchMapping("/{id}")
    @PreAuthorize("hasRole('SELLER')")
    public KitchenProfile update(
            Authentication auth, @PathVariable String id, @Valid @RequestBody UpdateKitchenRequest request) {
        return kitchens.update(auth.getName(), id, request);
    }

    /** Story 1.3 AC2 — gallery photos are images only (reports may also be PDF). */
    private static final java.util.Set<String> IMAGE_TYPES =
            java.util.Set.of("image/jpeg", "image/png", "image/webp");

    /** Story 1.3 AC2 — multipart photo upload (jpeg/png/webp, ≤5 MB, ≤10 per kitchen). */
    @PostMapping("/{id}/photos")
    @PreAuthorize("hasRole('SELLER')")
    public KitchenProfile uploadPhoto(
            Authentication auth, @PathVariable String id, @RequestParam("file") MultipartFile file)
            throws IOException {
        String contentType = file.getContentType();
        if (contentType == null || !IMAGE_TYPES.contains(contentType)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "UNSUPPORTED_PHOTO_TYPE");
        }
        return kitchens.addPhoto(auth.getName(), id, file.getBytes(), contentType);
    }

    @GetMapping("/{id}")
    public KitchenProfile profile(@PathVariable String id) {
        return kitchens.publicProfile(id);
    }

    @GetMapping("/{id}/menu")
    public MenuDayResponse menu(@PathVariable String id, @RequestParam(required = false) String date) {
        return kitchens.publishedMenu(id, date);
    }

    @PostMapping("/{id}/attestation")
    @PreAuthorize("hasRole('SELLER')")
    public Map<String, Object> attest(Authentication auth, @PathVariable String id, HttpServletRequest request) {
        return kitchens.attest(auth.getName(), id, request.getRemoteAddr());
    }
}
