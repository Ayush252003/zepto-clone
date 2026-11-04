package com.ayush.zepto.inventory.entity;

import com.ayush.zepto.catalog.entity.Product;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "inventory")
public class Inventory {
    @Id
    @Column(name = "product_id")
    private Long productId;

    @MapsId
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(name = "on_hand", nullable = false)
    private int onHand;

    @Column(nullable = false)
    private int reserved;

    @Column(name = "updated_at", insertable = false, updatable = false)
    private Instant updatedAt;

    protected Inventory() {}

    public Inventory(Product product) {
        this.product = product;
        this.onHand = 0;
        this.reserved = 0;
    }

    public Long getProductId() { return productId; }
    public Product getProduct() { return product; }
    public int getOnHand() { return onHand; }
    public int getReserved() { return reserved; }
    public int getAvailable() { return onHand - reserved; }
    public Instant getUpdatedAt() { return updatedAt; }
}
