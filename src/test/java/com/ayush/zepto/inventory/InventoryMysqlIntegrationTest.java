package com.ayush.zepto.inventory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.ayush.zepto.catalog.entity.Category;
import com.ayush.zepto.catalog.entity.Product;
import com.ayush.zepto.catalog.repository.CategoryRepository;
import com.ayush.zepto.catalog.repository.ProductRepository;
import com.ayush.zepto.common.error.ResourceConflictException;
import com.ayush.zepto.inventory.dto.InventoryResponse;
import com.ayush.zepto.inventory.service.InventoryService;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:mysql://localhost:3306/zepto_clone_test",
        "spring.datasource.username=${DB_USERNAME:root}",
        "spring.datasource.password=${DB_PASSWORD}"
})
@EnabledIfEnvironmentVariable(named = "INVENTORY_MYSQL_TESTS", matches = "true")
class InventoryMysqlIntegrationTest {
    @Autowired InventoryService inventoryService;
    @Autowired ProductRepository products;
    @Autowired CategoryRepository categories;
    @Autowired JdbcTemplate jdbc;

    private final List<Long> productIds = new ArrayList<>();
    private final List<Long> categoryIds = new ArrayList<>();
    private final ConcurrentLinkedQueue<String> reservationIds = new ConcurrentLinkedQueue<>();

    @BeforeEach
    void resetTrackedRows() {
        productIds.clear();
        categoryIds.clear();
        reservationIds.clear();
    }

    @AfterEach
    void removeOnlyRowsCreatedByThisTest() {
        for (String id : reservationIds) {
            jdbc.update("DELETE FROM inventory_reservation_items WHERE reservation_id = ?", id);
            jdbc.update("DELETE FROM inventory_reservations WHERE id = ?", id);
        }
        for (Long productId : productIds) {
            jdbc.update("DELETE FROM inventory WHERE product_id = ?", productId);
            products.deleteById(productId);
        }
        for (Long categoryId : categoryIds) categories.deleteById(categoryId);
    }

    @Test
    void reserveReleaseAndCommitChangeCountsExactlyOnce() {
        long productId = createProduct();
        inventoryService.setOnHand(productId, 4);
        String releasedId = reservationId();

        inventoryService.reserve(releasedId, Map.of(productId, 3));
        assertCounts(productId, 4, 3, 1);
        inventoryService.reserve(releasedId, Map.of(productId, 3));
        assertCounts(productId, 4, 3, 1);
        inventoryService.release(releasedId);
        inventoryService.release(releasedId);
        assertCounts(productId, 4, 0, 4);

        String committedId = reservationId();
        inventoryService.reserve(committedId, Map.of(productId, 4));
        inventoryService.commit(committedId);
        inventoryService.commit(committedId);
        assertCounts(productId, 0, 0, 0);
        assertThrows(ResourceConflictException.class, () -> inventoryService.release(committedId));
    }

    @Test
    void failedMultiProductReservationRollsBackEarlierStockChanges() {
        long first = createProduct();
        long second = createProduct();
        inventoryService.setOnHand(first, 3);
        String id = reservationId();

        assertThrows(ResourceConflictException.class,
                () -> inventoryService.reserve(id, Map.of(first, 2, second, 1)));

        assertCounts(first, 3, 0, 3);
        assertCounts(second, 0, 0, 0);
    }

    @Test
    void simultaneousReservationsCannotExceedAvailableStock() throws Exception {
        long productId = createProduct();
        int startingStock = 8;
        int requests = 16;
        inventoryService.setOnHand(productId, startingStock);
        CountDownLatch ready = new CountDownLatch(requests);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger successes = new AtomicInteger();
        AtomicInteger insufficient = new AtomicInteger();
        var executor = Executors.newFixedThreadPool(requests);
        try {
            List<java.util.concurrent.Future<?>> futures = new ArrayList<>();
            for (int i = 0; i < requests; i++) {
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    if (!start.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("Start signal timed out.");
                    try {
                        inventoryService.reserve(reservationId(), Map.of(productId, 1));
                        successes.incrementAndGet();
                    } catch (ResourceConflictException exception) {
                        insufficient.incrementAndGet();
                    }
                    return null;
                }));
            }
            if (!ready.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("Workers did not become ready.");
            start.countDown();
            for (var future : futures) future.get(45, TimeUnit.SECONDS);
        } finally {
            executor.shutdownNow();
        }

        assertEquals(startingStock, successes.get());
        assertEquals(requests - startingStock, insufficient.get());
        assertCounts(productId, startingStock, startingStock, 0);
    }

    @Test
    void administratorCannotSetStockBelowExistingReservations() {
        long productId = createProduct();
        inventoryService.setOnHand(productId, 5);
        inventoryService.reserve(reservationId(), Map.of(productId, 3));

        assertThrows(ResourceConflictException.class, () -> inventoryService.setOnHand(productId, 2));
        assertCounts(productId, 5, 3, 2);
    }

    private long createProduct() {
        String unique = UUID.randomUUID().toString().replace("-", "");
        Category category = categories.saveAndFlush(new Category("Inventory " + unique, "inventory-" + unique));
        categoryIds.add(category.getId());
        Product product = products.saveAndFlush(new Product("INV-" + unique, "Inventory test", null,
                category, new BigDecimal("1.00")));
        productIds.add(product.getId());
        inventoryService.initialize(product);
        return product.getId();
    }

    private String reservationId() {
        String id = UUID.randomUUID().toString();
        reservationIds.add(id);
        return id;
    }

    private void assertCounts(long productId, int onHand, int reserved, int available) {
        InventoryResponse inventory = inventoryService.get(productId);
        assertEquals(onHand, inventory.onHand());
        assertEquals(reserved, inventory.reserved());
        assertEquals(available, inventory.available());
    }
}
