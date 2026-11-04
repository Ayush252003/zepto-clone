package com.ayush.zepto.order.dto;

import com.ayush.zepto.order.entity.Order;
import com.ayush.zepto.order.entity.Payment;
import com.ayush.zepto.delivery.entity.Delivery;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record OrderResponse(Long id, String status, List<OrderLineResponse> items,
        BigDecimal total, String currency, String paymentStatus, Long deliveryId, String deliveryStatus,
        String courierName, Instant estimatedDeliveryAt, Instant createdAt) {
    public static OrderResponse from(Order order) {
        Payment payment = order.payment();
        Delivery delivery = order.getDelivery();
        return new OrderResponse(order.getId(), order.getStatus().name(),
                order.getItems().stream().map(OrderLineResponse::from).toList(), order.getTotalAmount(), "INR",
                payment == null ? null : payment.getStatus().name(),
                delivery == null ? null : delivery.getId(), delivery == null ? null : delivery.getStatus().name(),
                delivery == null ? null : delivery.getCourierName(),
                delivery == null ? null : delivery.getEtaAt(), order.getCreatedAt());
    }
}
