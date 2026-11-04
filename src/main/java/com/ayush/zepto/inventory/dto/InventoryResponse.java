package com.ayush.zepto.inventory.dto;

import com.ayush.zepto.inventory.entity.Inventory;
import java.time.Instant;

public record InventoryResponse(Long productId, int onHand, int reserved, int available, Instant updatedAt) {
    public static InventoryResponse from(Inventory inventory) {
        return new InventoryResponse(inventory.getProductId(), inventory.getOnHand(), inventory.getReserved(),
                inventory.getAvailable(), inventory.getUpdatedAt());
    }
}
