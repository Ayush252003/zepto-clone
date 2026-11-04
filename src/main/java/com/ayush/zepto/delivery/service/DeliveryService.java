package com.ayush.zepto.delivery.service;

import com.ayush.zepto.common.error.ResourceConflictException;
import com.ayush.zepto.common.error.ResourceNotFoundException;
import com.ayush.zepto.delivery.dto.DeliveryResponse;
import com.ayush.zepto.delivery.dto.UpdateDeliveryRequest;
import com.ayush.zepto.delivery.entity.Delivery;
import com.ayush.zepto.delivery.entity.DeliveryStatus;
import com.ayush.zepto.delivery.repository.DeliveryRepository;
import com.ayush.zepto.notification.service.NotificationService;
import com.ayush.zepto.order.entity.Order;
import com.ayush.zepto.user.User;
import com.ayush.zepto.user.UserRepository;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DeliveryService {
    private final DeliveryRepository deliveries;
    private final UserRepository users;
    private final NotificationService notifications;

    public DeliveryService(DeliveryRepository deliveries, UserRepository users, NotificationService notifications) {
        this.deliveries = deliveries;
        this.users = users;
        this.notifications = notifications;
    }

    @Transactional
    public Delivery createFor(Order order) {
        Delivery delivery = new Delivery(order, Instant.now().plusSeconds(30 * 60L));
        order.setDelivery(delivery);
        Delivery saved = deliveries.saveAndFlush(delivery);
        notifications.publish(order, "DELIVERY_PREPARING", "Your order is being prepared for delivery.");
        return saved;
    }

    @Transactional(readOnly = true)
    public DeliveryResponse forCustomer(String email, Long orderId) {
        User user = users.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User account not found."));
        Delivery delivery = deliveries.findByOrder_IdAndOrder_User_Id(orderId, user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Delivery not found."));
        return DeliveryResponse.from(delivery);
    }

    @Transactional
    public DeliveryResponse update(Long deliveryId, UpdateDeliveryRequest request) {
        Delivery delivery = deliveries.findByIdForUpdate(deliveryId)
                .orElseThrow(() -> new ResourceNotFoundException("Delivery not found."));
        try {
            delivery.advance(request.status(), request.courierName(), request.etaMinutes());
        } catch (IllegalArgumentException exception) {
            throw new ResourceConflictException(exception.getMessage());
        }
        Delivery saved = deliveries.saveAndFlush(delivery);
        notifications.publish(saved.getOrder(), "DELIVERY_" + saved.getStatus().name(),
                "Delivery status changed to " + saved.getStatus().name() + ".");
        return DeliveryResponse.from(saved);
    }
}
