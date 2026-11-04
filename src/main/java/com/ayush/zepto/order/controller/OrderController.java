package com.ayush.zepto.order.controller;

import com.ayush.zepto.order.dto.OrderResponse;
import com.ayush.zepto.order.dto.PaymentSimulationRequest;
import com.ayush.zepto.order.service.OrderService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/orders")
public class OrderController {
    private final OrderService service;

    public OrderController(OrderService service) { this.service = service; }

    @PostMapping
    public ResponseEntity<OrderResponse> checkout(@AuthenticationPrincipal Jwt jwt,
            @RequestHeader(value = "Idempotency-Key", required = false) String key) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.checkout(jwt.getSubject(), key));
    }

    @GetMapping
    public List<OrderResponse> history(@AuthenticationPrincipal Jwt jwt) {
        return service.history(jwt.getSubject());
    }

    @GetMapping("/{orderId}")
    public OrderResponse get(@AuthenticationPrincipal Jwt jwt, @PathVariable Long orderId) {
        return service.get(jwt.getSubject(), orderId);
    }

    @PostMapping("/{orderId}/payment")
    public OrderResponse pay(@AuthenticationPrincipal Jwt jwt, @PathVariable Long orderId,
            @RequestHeader(value = "Idempotency-Key", required = false) String key,
            @Valid @RequestBody PaymentSimulationRequest request) {
        return service.simulatePayment(jwt.getSubject(), orderId, key, request.outcome());
    }

    @PostMapping("/{orderId}/cancel")
    public OrderResponse cancel(@AuthenticationPrincipal Jwt jwt, @PathVariable Long orderId) {
        return service.cancel(jwt.getSubject(), orderId);
    }
}
