package com.ayush.zepto.cart;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ayush.zepto.cart.dto.CartResponse;
import com.ayush.zepto.cart.service.CartService;
import com.ayush.zepto.catalog.entity.Category;
import com.ayush.zepto.catalog.entity.Product;
import com.ayush.zepto.catalog.repository.CategoryRepository;
import com.ayush.zepto.catalog.repository.ProductRepository;
import com.ayush.zepto.common.error.BadRequestException;
import com.ayush.zepto.common.error.ResourceConflictException;
import com.ayush.zepto.common.error.ResourceNotFoundException;
import com.ayush.zepto.inventory.service.InventoryService;
import com.ayush.zepto.user.Role;
import com.ayush.zepto.user.User;
import com.ayush.zepto.user.UserRepository;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:mysql://localhost:3306/zepto_clone_test",
        "spring.datasource.username=${DB_USERNAME:root}",
        "spring.datasource.password=${DB_PASSWORD}"
})
@EnabledIfEnvironmentVariable(named = "INVENTORY_MYSQL_TESTS", matches = "true")
class CartMysqlIntegrationTest {
    @Autowired CartService cartService;
    @Autowired ProductRepository products;
    @Autowired CategoryRepository categories;
    @Autowired UserRepository users;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired InventoryService inventoryService;
    @Autowired JdbcTemplate jdbc;

    private final List<Long> productIds = new ArrayList<>();
    private final List<Long> categoryIds = new ArrayList<>();
    private final List<Long> userIds = new ArrayList<>();

    @BeforeEach
    void resetTrackedRows() {
        productIds.clear();
        categoryIds.clear();
        userIds.clear();
    }

    @AfterEach
    void removeOnlyRowsCreatedByThisTest() {
        for (Long userId : userIds) {
            jdbc.update("DELETE FROM cart_items WHERE cart_id IN (SELECT id FROM carts WHERE user_id = ?)", userId);
            jdbc.update("DELETE FROM carts WHERE user_id = ?", userId);
        }
        for (Long productId : productIds) {
            jdbc.update("DELETE FROM inventory WHERE product_id = ?", productId);
            products.deleteById(productId);
        }
        for (Long categoryId : categoryIds) categories.deleteById(categoryId);
        for (Long userId : userIds) users.deleteById(userId);
    }

    @Test
    void customerCanAddIncrementReplaceAndRemoveOnlyTheirOwnItems() {
        String owner = createCustomer();
        String otherCustomer = createCustomer();
        long productId = createProduct("10.00");

        assertTrue(cartService.get(owner).items().isEmpty());
        assertEquals(new BigDecimal("0.00"), cartService.get(owner).subtotal());
        CartResponse added = cartService.add(owner, productId, 2);
        assertEquals(2, added.items().getFirst().quantity());
        CartResponse incremented = cartService.add(owner, productId, 1);
        assertEquals(3, incremented.items().getFirst().quantity());
        assertEquals(new BigDecimal("30.00"), incremented.subtotal());

        CartResponse otherCart = cartService.get(otherCustomer);
        assertTrue(otherCart.items().isEmpty());
        CartResponse replaced = cartService.setQuantity(owner, productId, 5);
        assertEquals(5, replaced.items().getFirst().quantity());
        cartService.remove(owner, productId);
        cartService.remove(owner, productId);
        assertTrue(cartService.get(owner).items().isEmpty());
        assertEquals(0, inventoryService.get(productId).reserved());
    }

    @Test
    void priceAndAvailabilityAreRefreshedAndInactiveItemsRemainRemovable() {
        String owner = createCustomer();
        long productId = createProduct("5.00");
        inventoryService.setOnHand(productId, 2);

        CartResponse added = cartService.add(owner, productId, 3);
        assertEquals(2, added.items().getFirst().availableNow());
        assertEquals(3, added.items().getFirst().quantity());
        assertEquals(0, inventoryService.get(productId).reserved());

        Product product = products.findById(productId).orElseThrow();
        product.update(product.getSku(), product.getName(), product.getDescription(), product.getCategory(),
                new BigDecimal("7.00"));
        products.saveAndFlush(product);
        assertEquals(new BigDecimal("21.00"), cartService.get(owner).subtotal());

        product.deactivate();
        products.saveAndFlush(product);
        assertFalse(cartService.get(owner).items().getFirst().active());
        assertThrows(ResourceConflictException.class, () -> cartService.add(owner, productId, 1));
        assertThrows(ResourceConflictException.class, () -> cartService.setQuantity(owner, productId, 2));
        cartService.remove(owner, productId);
        assertTrue(cartService.get(owner).items().isEmpty());
    }

    @Test
    void simultaneousAddsToOneCartDoNotLoseQuantityUpdates() throws Exception {
        String owner = createCustomer();
        long productId = createProduct("1.00");
        int updates = 16;
        CountDownLatch ready = new CountDownLatch(updates);
        CountDownLatch start = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(updates);
        try {
            List<java.util.concurrent.Future<?>> futures = new ArrayList<>();
            for (int i = 0; i < updates; i++) {
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    if (!start.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("Start signal timed out.");
                    cartService.add(owner, productId, 1);
                    return null;
                }));
            }
            if (!ready.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("Workers did not become ready.");
            start.countDown();
            for (var future : futures) future.get(45, TimeUnit.SECONDS);
        } finally {
            executor.shutdownNow();
        }

        assertEquals(updates, cartService.get(owner).items().getFirst().quantity());
        Long lines = jdbc.queryForObject("SELECT COUNT(*) FROM cart_items WHERE product_id = ?", Long.class, productId);
        assertEquals(1L, lines);
    }

    @Test
    void rejectsInvalidQuantitiesAndMissingProductsOrCartLines() {
        String owner = createCustomer();

        assertThrows(BadRequestException.class, () -> cartService.add(owner, 1L, 0));
        assertThrows(ResourceNotFoundException.class, () -> cartService.add(owner, Long.MAX_VALUE, 1));
        assertThrows(ResourceNotFoundException.class, () -> cartService.setQuantity(owner, 1L, 1));
    }

    private String createCustomer() {
        String email = "cart-" + UUID.randomUUID().toString().replace("-", "") + "@example.invalid";
        User user = users.saveAndFlush(new User(email, passwordEncoder.encode("Test-Cart-Pass-2026!"), Role.CUSTOMER));
        userIds.add(user.getId());
        return email;
    }

    private long createProduct(String price) {
        String unique = UUID.randomUUID().toString().replace("-", "");
        Category category = categories.saveAndFlush(new Category("Cart " + unique, "cart-" + unique));
        categoryIds.add(category.getId());
        Product product = products.saveAndFlush(new Product("CART-" + unique, "Cart item", null,
                category, new BigDecimal(price)));
        productIds.add(product.getId());
        inventoryService.initialize(product);
        return product.getId();
    }
}
