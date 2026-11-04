package com.ayush.zepto.delivery.controller;

import com.ayush.zepto.delivery.dto.DeliveryResponse;
import com.ayush.zepto.delivery.dto.UpdateDeliveryRequest;
import com.ayush.zepto.delivery.service.DeliveryService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class DeliveryController {
    private final DeliveryService service;

    public DeliveryController(DeliveryService service) { this.service = service; }

    @GetMapping("/api/v1/orders/{orderId}/delivery")
    public DeliveryResponse forCustomer(@AuthenticationPrincipal Jwt jwt, @PathVariable Long orderId) {
        return service.forCustomer(jwt.getSubject(), orderId);
    }

    @PatchMapping("/api/v1/admin/deliveries/{deliveryId}")
    public DeliveryResponse update(@PathVariable Long deliveryId,
            @Valid @RequestBody UpdateDeliveryRequest request) {
        return service.update(deliveryId, request);
    }
}
