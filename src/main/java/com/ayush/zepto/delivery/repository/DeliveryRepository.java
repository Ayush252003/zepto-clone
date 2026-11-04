package com.ayush.zepto.delivery.repository;

import com.ayush.zepto.delivery.entity.Delivery;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DeliveryRepository extends JpaRepository<Delivery, Long> {
    @EntityGraph(attributePaths = {"order", "order.user"})
    Optional<Delivery> findByOrder_IdAndOrder_User_Id(Long orderId, Long userId);

    @EntityGraph(attributePaths = {"order", "order.user"})
    Optional<Delivery> findById(Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = {"order", "order.user"})
    @Query("select d from Delivery d where d.id = :id")
    Optional<Delivery> findByIdForUpdate(@Param("id") Long id);
}
