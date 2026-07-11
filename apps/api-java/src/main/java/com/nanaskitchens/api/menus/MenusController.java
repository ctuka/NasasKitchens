package com.nanaskitchens.api.menus;

import com.nanaskitchens.api.kitchens.dto.MenuDayResponse;
import com.nanaskitchens.api.menus.dto.CreateMenuDayRequest;
import com.nanaskitchens.api.menus.dto.DishRequest;
import com.nanaskitchens.api.menus.dto.UpdateMenuDayRequest;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Seller-facing menu management (Story 2.1/2.2). Buyers read menus via GET /kitchens/{id}/menu. */
@RestController
@RequestMapping("/kitchens/{kitchenId}")
@PreAuthorize("hasRole('SELLER')")
public class MenusController {

    private final MenusService menus;

    public MenusController(MenusService menus) {
        this.menus = menus;
    }

    // ── Dishes ────────────────────────────────────────────────────────────────

    @PostMapping("/dishes")
    public MenuDayResponse.Dish createDish(
            Authentication auth, @PathVariable String kitchenId, @Valid @RequestBody DishRequest request) {
        return menus.createDish(auth.getName(), kitchenId, request);
    }

    @GetMapping("/dishes")
    public List<MenuDayResponse.Dish> listDishes(Authentication auth, @PathVariable String kitchenId) {
        return menus.listDishes(auth.getName(), kitchenId);
    }

    @PatchMapping("/dishes/{dishId}")
    public MenuDayResponse.Dish updateDish(
            Authentication auth,
            @PathVariable String kitchenId,
            @PathVariable String dishId,
            @Valid @RequestBody DishRequest.Patch request) {
        return menus.updateDish(auth.getName(), kitchenId, dishId, request);
    }

    @DeleteMapping("/dishes/{dishId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteDish(Authentication auth, @PathVariable String kitchenId, @PathVariable String dishId) {
        menus.deleteDish(auth.getName(), kitchenId, dishId);
    }

    // ── Menu days ─────────────────────────────────────────────────────────────

    @PostMapping("/menu-days")
    public MenuDayResponse createMenuDay(
            Authentication auth,
            @PathVariable String kitchenId,
            @Valid @RequestBody CreateMenuDayRequest request) {
        return menus.createMenuDay(auth.getName(), kitchenId, request);
    }

    @GetMapping("/menu-days")
    public List<MenuDayResponse> listMenuDays(
            Authentication auth, @PathVariable String kitchenId, @RequestParam(required = false) String date) {
        return menus.listMenuDays(auth.getName(), kitchenId, date);
    }

    @PatchMapping("/menu-days/{menuDayId}")
    public MenuDayResponse updateMenuDay(
            Authentication auth,
            @PathVariable String kitchenId,
            @PathVariable String menuDayId,
            @Valid @RequestBody UpdateMenuDayRequest request) {
        return menus.updateMenuDay(auth.getName(), kitchenId, menuDayId, request);
    }

    @PostMapping("/menu-days/{menuDayId}/publish")
    public MenuDayResponse publish(
            Authentication auth, @PathVariable String kitchenId, @PathVariable String menuDayId) {
        return menus.publish(auth.getName(), kitchenId, menuDayId);
    }
}
