package com.ayush.zepto.delivery.dto;

import com.ayush.zepto.delivery.entity.Delivery;
import java.time.Instant;

public record DeliveryResponse(Long id, Long orderId, String status, String courierName, Instant etaAt) {
    public static DeliveryResponse from(Delivery delivery) {
        return new DeliveryResponse(delivery.getId(), delivery.getOrder().getId(), delivery.getStatus().name(),
                delivery.getCourierName(), delivery.getEtaAt());
    }
}
