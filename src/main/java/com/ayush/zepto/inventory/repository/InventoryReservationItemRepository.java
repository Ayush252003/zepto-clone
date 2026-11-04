package com.ayush.zepto.inventory.repository;

import com.ayush.zepto.inventory.entity.InventoryReservationItem;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InventoryReservationItemRepository extends JpaRepository<InventoryReservationItem, Long> {
    List<InventoryReservationItem> findAllByReservation_IdOrderByProduct_IdAsc(String reservationId);
}
