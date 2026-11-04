package com.ayush.zepto.notification.repository;

import com.ayush.zepto.notification.entity.Notification;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationRepository extends JpaRepository<Notification, Long> {
    @EntityGraph(attributePaths = {"order"})
    List<Notification> findAllByUser_IdOrderByCreatedAtDesc(Long userId);

    @EntityGraph(attributePaths = {"order"})
    Optional<Notification> findByIdAndUser_Id(Long id, Long userId);
}
