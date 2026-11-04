package com.ayush.zepto.inventory.repository;

import com.ayush.zepto.inventory.entity.Inventory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface InventoryRepository extends JpaRepository<Inventory, Long> {
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update Inventory i set i.onHand = :onHand where i.productId = :productId and i.reserved <= :onHand")
    int setOnHandIfNotBelowReserved(@Param("productId") Long productId, @Param("onHand") int onHand);

    @Modifying(flushAutomatically = true)
    @Query("update Inventory i set i.reserved = i.reserved + :quantity "
            + "where i.productId = :productId and i.onHand - i.reserved >= :quantity")
    int reserveIfAvailable(@Param("productId") Long productId, @Param("quantity") int quantity);

    @Modifying(flushAutomatically = true)
    @Query("update Inventory i set i.onHand = i.onHand - :quantity, i.reserved = i.reserved - :quantity "
            + "where i.productId = :productId and i.reserved >= :quantity and i.onHand >= :quantity")
    int commitReserved(@Param("productId") Long productId, @Param("quantity") int quantity);

    @Modifying(flushAutomatically = true)
    @Query("update Inventory i set i.reserved = i.reserved - :quantity "
            + "where i.productId = :productId and i.reserved >= :quantity")
    int releaseReserved(@Param("productId") Long productId, @Param("quantity") int quantity);
}
