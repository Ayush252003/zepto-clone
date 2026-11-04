package com.ayush.zepto.inventory.service;

import com.ayush.zepto.catalog.entity.Product;
import com.ayush.zepto.catalog.repository.ProductRepository;
import com.ayush.zepto.common.error.BadRequestException;
import com.ayush.zepto.common.error.ResourceConflictException;
import com.ayush.zepto.common.error.ResourceNotFoundException;
import com.ayush.zepto.inventory.dto.InventoryResponse;
import com.ayush.zepto.inventory.entity.Inventory;
import com.ayush.zepto.inventory.entity.InventoryReservation;
import com.ayush.zepto.inventory.entity.InventoryReservationItem;
import com.ayush.zepto.inventory.entity.InventoryReservationStatus;
import com.ayush.zepto.inventory.repository.InventoryRepository;
import com.ayush.zepto.inventory.repository.InventoryReservationItemRepository;
import com.ayush.zepto.inventory.repository.InventoryReservationRepository;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InventoryService {
    private final InventoryRepository inventoryRepository;
    private final InventoryReservationRepository reservationRepository;
    private final InventoryReservationItemRepository itemRepository;
    private final ProductRepository productRepository;

    public InventoryService(InventoryRepository inventoryRepository,
            InventoryReservationRepository reservationRepository,
            InventoryReservationItemRepository itemRepository,
            ProductRepository productRepository) {
        this.inventoryRepository = inventoryRepository;
        this.reservationRepository = reservationRepository;
        this.itemRepository = itemRepository;
        this.productRepository = productRepository;
    }

    @Transactional
    public void initialize(Product product) {
        inventoryRepository.saveAndFlush(new Inventory(product));
    }

    @Transactional(readOnly = true)
    public InventoryResponse get(Long productId) {
        return inventoryRepository.findById(productId).map(InventoryResponse::from)
                .orElseThrow(() -> new ResourceNotFoundException("Inventory not found."));
    }

    @Transactional
    public InventoryResponse setOnHand(Long productId, int onHand) {
        if (onHand < 0) throw new BadRequestException("onHand must be zero or greater.");
        Inventory inventory = inventoryRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Inventory not found."));
        int updated = inventoryRepository.setOnHandIfNotBelowReserved(productId, onHand);
        if (updated == 0) {
            throw new ResourceConflictException("onHand cannot be less than the quantity already reserved.");
        }
        return inventoryRepository.findById(productId).map(InventoryResponse::from)
                .orElseThrow(() -> new IllegalStateException("Inventory row disappeared during stock update."));
    }

    /** Reserves all requested units atomically. The caller supplies a stable UUID for retry safety. */
    @Transactional
    public void reserve(String rawReservationId, Map<Long, Integer> requestedItems) {
        String reservationId = normalizeReservationId(rawReservationId);
        Map<Long, Integer> quantities = validateItems(requestedItems);
        int created = reservationRepository.insertIfAbsent(reservationId);
        InventoryReservation reservation = reservationRepository.findByIdForUpdate(reservationId)
                .orElseThrow(() -> new IllegalStateException("Inventory reservation header was not created."));
        if (created == 0) {
            if (reservation.getStatus() != InventoryReservationStatus.RESERVED
                    || !storedItems(reservationId).equals(quantities)) {
                throw new ResourceConflictException("Reservation ID is already used with different or completed contents.");
            }
            return;
        }
        for (var item : quantities.entrySet()) {
            if (!productRepository.existsById(item.getKey())) {
                throw new ResourceNotFoundException("Product " + item.getKey() + " not found.");
            }
            int updated = inventoryRepository.reserveIfAvailable(item.getKey(), item.getValue());
            if (updated != 1) {
                throw new ResourceConflictException("Insufficient available stock for product " + item.getKey() + ".");
            }
        }
        List<InventoryReservationItem> rows = quantities.entrySet().stream()
                .map(item -> new InventoryReservationItem(reservation, productRepository.getReferenceById(item.getKey()),
                        item.getValue()))
                .toList();
        itemRepository.saveAllAndFlush(rows);
    }

    @Transactional
    public void commit(String rawReservationId) {
        finishReservation(rawReservationId, InventoryReservationStatus.COMMITTED);
    }

    @Transactional
    public void release(String rawReservationId) {
        finishReservation(rawReservationId, InventoryReservationStatus.RELEASED);
    }

    private void finishReservation(String rawId, InventoryReservationStatus target) {
        String id = normalizeReservationId(rawId);
        InventoryReservation reservation = reservationRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new ResourceNotFoundException("Inventory reservation not found."));
        if (reservation.getStatus() == target) return;
        if (reservation.getStatus() != InventoryReservationStatus.RESERVED) {
            throw new ResourceConflictException("A completed inventory reservation cannot be changed.");
        }
        List<InventoryReservationItem> items = itemRepository.findAllByReservation_IdOrderByProduct_IdAsc(id);
        for (InventoryReservationItem item : items) {
            int updated = target == InventoryReservationStatus.COMMITTED
                    ? inventoryRepository.commitReserved(item.getProduct().getId(), item.getQuantity())
                    : inventoryRepository.releaseReserved(item.getProduct().getId(), item.getQuantity());
            if (updated != 1) {
                throw new IllegalStateException("Inventory reservation counts are inconsistent for product "
                        + item.getProduct().getId() + ".");
            }
        }
        if (target == InventoryReservationStatus.COMMITTED) reservation.markCommitted();
        else reservation.markReleased();
        reservationRepository.saveAndFlush(reservation);
    }

    private Map<Long, Integer> storedItems(String reservationId) {
        Map<Long, Integer> items = new LinkedHashMap<>();
        for (InventoryReservationItem item : itemRepository.findAllByReservation_IdOrderByProduct_IdAsc(reservationId)) {
            items.put(item.getProduct().getId(), item.getQuantity());
        }
        return items;
    }

    private Map<Long, Integer> validateItems(Map<Long, Integer> requestedItems) {
        if (requestedItems == null || requestedItems.isEmpty()) {
            throw new BadRequestException("A reservation must contain at least one product.");
        }
        Map<Long, Integer> sorted = new TreeMap<>();
        requestedItems.forEach((productId, quantity) -> {
            if (productId == null || productId < 1) throw new BadRequestException("Product IDs must be positive.");
            if (quantity == null || quantity < 1) throw new BadRequestException("Reservation quantities must be positive.");
            sorted.put(productId, quantity);
        });
        return sorted;
    }

    private String normalizeReservationId(String rawId) {
        try {
            return UUID.fromString(rawId).toString();
        } catch (RuntimeException exception) {
            throw new BadRequestException("Reservation ID must be a UUID.");
        }
    }
}
