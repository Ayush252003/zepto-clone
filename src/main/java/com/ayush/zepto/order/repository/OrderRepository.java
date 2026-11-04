package com.ayush.zepto.order.repository;

import com.ayush.zepto.order.entity.Order;
import java.util.List;
import java.util.Optional;
import com.ayush.zepto.order.entity.OrderStatus;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderRepository extends JpaRepository<Order, Long> {
    boolean existsByUser_IdAndStatus(Long userId, OrderStatus status);

    @EntityGraph(attributePaths = {"items", "items.product", "payment", "delivery"})
    Optional<Order> findByUser_IdAndIdempotencyKey(Long userId, String idempotencyKey);

    @EntityGraph(attributePaths = {"items", "items.product", "payment", "delivery"})
    Optional<Order> findByIdAndUser_Id(Long id, Long userId);

    @EntityGraph(attributePaths = {"items", "items.product", "payment", "delivery"})
    List<Order> findAllByUser_IdOrderByCreatedAtDesc(Long userId);
}
