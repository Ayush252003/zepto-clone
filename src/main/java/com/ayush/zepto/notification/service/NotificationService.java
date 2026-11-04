package com.ayush.zepto.notification.service;

import com.ayush.zepto.common.error.ResourceNotFoundException;
import com.ayush.zepto.notification.dto.NotificationResponse;
import com.ayush.zepto.notification.entity.Notification;
import com.ayush.zepto.notification.repository.NotificationRepository;
import com.ayush.zepto.order.entity.Order;
import com.ayush.zepto.user.User;
import com.ayush.zepto.user.UserRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NotificationService {
    private final NotificationRepository notifications;
    private final UserRepository users;

    public NotificationService(NotificationRepository notifications, UserRepository users) {
        this.notifications = notifications;
        this.users = users;
    }

    @Transactional
    public void publish(Order order, String kind, String message) {
        notifications.save(new Notification(order.getUser(), order, kind, message));
    }

    @Transactional(readOnly = true)
    public List<NotificationResponse> list(String email) {
        User user = users.findByEmail(email).orElseThrow(() -> new ResourceNotFoundException("User account not found."));
        return notifications.findAllByUser_IdOrderByCreatedAtDesc(user.getId()).stream()
                .map(NotificationResponse::from).toList();
    }

    @Transactional
    public NotificationResponse markRead(String email, Long notificationId) {
        User user = users.findByEmailForUpdate(email)
                .orElseThrow(() -> new ResourceNotFoundException("User account not found."));
        Notification notification = notifications.findByIdAndUser_Id(notificationId, user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Notification not found."));
        notification.markRead();
        return NotificationResponse.from(notifications.saveAndFlush(notification));
    }
}
