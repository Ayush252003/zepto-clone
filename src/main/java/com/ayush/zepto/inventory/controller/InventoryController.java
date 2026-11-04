package com.ayush.zepto.inventory.controller;

import com.ayush.zepto.inventory.dto.InventoryResponse;
import com.ayush.zepto.inventory.dto.SetStockRequest;
import com.ayush.zepto.inventory.service.InventoryService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/products/{productId}/inventory")
public class InventoryController {
    private final InventoryService inventoryService;

    public InventoryController(InventoryService inventoryService) {
        this.inventoryService = inventoryService;
    }

    @GetMapping
    public InventoryResponse get(@PathVariable Long productId) {
        return inventoryService.get(productId);
    }

    @PutMapping
    public InventoryResponse setStock(@PathVariable Long productId, @Valid @RequestBody SetStockRequest request) {
        return inventoryService.setOnHand(productId, request.onHand());
    }
}
