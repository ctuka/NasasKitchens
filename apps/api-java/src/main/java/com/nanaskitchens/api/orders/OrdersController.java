package com.nanaskitchens.api.orders;

import com.nanaskitchens.api.orders.dto.CreateOrderRequest;
import com.nanaskitchens.api.orders.dto.OrderDetailResponse;
import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/orders")
public class OrdersController {

    private final OrdersService orders;

    public OrdersController(OrdersService orders) {
        this.orders = orders;
    }

    @PostMapping
    public Map<String, Object> place(Authentication auth, @Valid @RequestBody CreateOrderRequest request) {
        return orders.place(auth.getName(), request);
    }

    @GetMapping("/{id}")
    public OrderDetailResponse detail(Authentication auth, @PathVariable String id) {
        return orders.detail(auth.getName(), id);
    }

    @PostMapping("/{id}/cancel")
    public Map<String, Object> cancel(Authentication auth, @PathVariable String id) {
        return orders.cancel(auth.getName(), id);
    }
}
