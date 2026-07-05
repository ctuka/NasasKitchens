package com.nanaskitchens.api.kitchens;

import com.nanaskitchens.api.kitchens.dto.CreateKitchenRequest;
import com.nanaskitchens.api.kitchens.dto.KitchenProfile;
import com.nanaskitchens.api.kitchens.dto.KitchenSearchResult;
import com.nanaskitchens.api.kitchens.dto.MenuDayResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
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
