package com.ayush.zepto.notification.dto;

import com.ayush.zepto.notification.entity.Notification;
import java.time.Instant;

public record NotificationResponse(Long id, Long orderId, String kind, String message,
        Instant createdAt, Instant readAt) {
    public static NotificationResponse from(Notification notification) {
        return new NotificationResponse(notification.getId(), notification.getOrderId(), notification.getKind(),
                notification.getMessage(), notification.getCreatedAt(), notification.getReadAt());
    }
}
