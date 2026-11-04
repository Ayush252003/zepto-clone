package com.ayush.zepto.inventory.entity;

import com.ayush.zepto.catalog.entity.Product;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(name = "inventory_reservation_items",
        uniqueConstraints = @UniqueConstraint(name = "uk_inventory_reservation_product",
                columnNames = {"reservation_id", "product_id"}))
public class InventoryReservationItem {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "reservation_id", nullable = false)
    private InventoryReservation reservation;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(nullable = false)
    private int quantity;

    protected InventoryReservationItem() {}

    public InventoryReservationItem(InventoryReservation reservation, Product product, int quantity) {
        this.reservation = reservation;
        this.product = product;
        this.quantity = quantity;
    }

    public Long getId() { return id; }
    public InventoryReservation getReservation() { return reservation; }
    public Product getProduct() { return product; }
    public int getQuantity() { return quantity; }
}
