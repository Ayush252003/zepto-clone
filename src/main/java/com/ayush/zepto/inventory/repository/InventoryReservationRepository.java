package com.ayush.zepto.inventory.repository;

import com.ayush.zepto.inventory.entity.InventoryReservation;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface InventoryReservationRepository extends JpaRepository<InventoryReservation, String> {
    @Modifying(flushAutomatically = true)
    @Query(value = "insert ignore into inventory_reservations (id, status) values (:id, 'RESERVED')",
            nativeQuery = true)
    int insertIfAbsent(@Param("id") String id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from InventoryReservation r where r.id = :id")
    Optional<InventoryReservation> findByIdForUpdate(@Param("id") String id);
}
