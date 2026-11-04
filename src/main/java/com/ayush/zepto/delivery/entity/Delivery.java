package com.ayush.zepto.delivery.entity;

import com.ayush.zepto.order.entity.Order;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "deliveries")
public class Delivery {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false, unique = true)
    private Order order;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private DeliveryStatus status;

    @Column(name = "courier_name", length = 100)
    private String courierName;

    @Column(name = "eta_at", nullable = false)
    private Instant etaAt;

    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", insertable = false, updatable = false)
    private Instant updatedAt;

    protected Delivery() {}

    public Delivery(Order order, Instant etaAt) {
        this.order = order;
        this.etaAt = etaAt;
        this.status = DeliveryStatus.PREPARING;
    }

    public void advance(DeliveryStatus next, String courierName, Integer etaMinutes) {
        if (next.ordinal() != status.ordinal() + 1) {
            throw new IllegalArgumentException("Delivery status must advance exactly one step.");
        }
        if (next == DeliveryStatus.ASSIGNED && (courierName == null || courierName.isBlank())) {
            throw new IllegalArgumentException("Courier name is required when assigning a delivery.");
        }
        if (courierName != null && !courierName.isBlank()) this.courierName = courierName.trim();
        if (etaMinutes != null) this.etaAt = Instant.now().plusSeconds(etaMinutes * 60L);
        this.status = next;
    }

    public Long getId() { return id; }
    public Order getOrder() { return order; }
    public DeliveryStatus getStatus() { return status; }
    public String getCourierName() { return courierName; }
    public Instant getEtaAt() { return etaAt; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
