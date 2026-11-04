package com.ayush.zepto.inventory.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "inventory_reservations")
public class InventoryReservation {
    @Id
    @Column(length = 36)
    private String id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private InventoryReservationStatus status;

    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;

    protected InventoryReservation() {}

    public InventoryReservation(String id) {
        this.id = id;
        this.status = InventoryReservationStatus.RESERVED;
    }

    public void markCommitted() { this.status = InventoryReservationStatus.COMMITTED; }
    public void markReleased() { this.status = InventoryReservationStatus.RELEASED; }
    public String getId() { return id; }
    public InventoryReservationStatus getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
}
