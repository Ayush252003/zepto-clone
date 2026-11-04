package com.ayush.zepto.order;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.ayush.zepto.cart.service.CartService;
import com.ayush.zepto.catalog.entity.Category;
import com.ayush.zepto.catalog.entity.Product;
import com.ayush.zepto.catalog.repository.CategoryRepository;
import com.ayush.zepto.catalog.repository.ProductRepository;
import com.ayush.zepto.common.error.ResourceConflictException;
import com.ayush.zepto.common.error.ResourceNotFoundException;
import com.ayush.zepto.delivery.dto.DeliveryResponse;
import com.ayush.zepto.delivery.dto.UpdateDeliveryRequest;
import com.ayush.zepto.delivery.entity.DeliveryStatus;
import com.ayush.zepto.delivery.service.DeliveryService;
import com.ayush.zepto.inventory.dto.InventoryResponse;
import com.ayush.zepto.inventory.service.InventoryService;
import com.ayush.zepto.notification.dto.NotificationResponse;
import com.ayush.zepto.notification.service.NotificationService;
import com.ayush.zepto.order.dto.OrderResponse;
import com.ayush.zepto.order.entity.PaymentOutcome;
import com.ayush.zepto.order.service.OrderService;
import com.ayush.zepto.user.Role;
import com.ayush.zepto.user.User;
import com.ayush.zepto.user.UserRepository;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
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
class OrderMysqlIntegrationTest {
    @Autowired OrderService orderService;
    @Autowired CartService cartService;
    @Autowired InventoryService inventoryService;
    @Autowired DeliveryService deliveryService;
    @Autowired NotificationService notificationService;
    @Autowired ProductRepository products;
    @Autowired CategoryRepository categories;
    @Autowired UserRepository users;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired JdbcTemplate jdbc;

    private final List<Long> productIds = new ArrayList<>();
    private final List<Long> categoryIds = new ArrayList<>();
    private final List<Long> userIds = new ArrayList<>();
    private final List<Long> orderIds = new ArrayList<>();
    private final List<String> reservationIds = new ArrayList<>();

    @BeforeEach
    void clearTrackedIds() {
        productIds.clear(); categoryIds.clear(); userIds.clear(); orderIds.clear(); reservationIds.clear();
    }

    @AfterEach
    void deleteOnlyRowsCreatedByThisTest() {
        for (Long orderId : orderIds) {
            jdbc.update("DELETE FROM notifications WHERE order_id = ?", orderId);
            jdbc.update("DELETE FROM deliveries WHERE order_id = ?", orderId);
            jdbc.update("DELETE FROM payments WHERE order_id = ?", orderId);
            jdbc.update("DELETE FROM order_items WHERE order_id = ?", orderId);
            jdbc.update("DELETE FROM orders WHERE id = ?", orderId);
        }
        for (Long userId : userIds) {
            jdbc.update("DELETE FROM cart_items WHERE cart_id IN (SELECT id FROM carts WHERE user_id = ?)", userId);
            jdbc.update("DELETE FROM carts WHERE user_id = ?", userId);
        }
        for (String reservationId : reservationIds) {
            jdbc.update("DELETE FROM inventory_reservation_items WHERE reservation_id = ?", reservationId);
            jdbc.update("DELETE FROM inventory_reservations WHERE id = ?", reservationId);
        }
        for (Long productId : productIds) {
            jdbc.update("DELETE FROM inventory WHERE product_id = ?", productId);
            products.deleteById(productId);
        }
        for (Long categoryId : categoryIds) categories.deleteById(categoryId);
        for (Long userId : userIds) users.deleteById(userId);
    }

    @Test
    void checkoutSnapshotsPriceAndSuccessCommitsStockIdempotently() {
        String owner = createCustomer();
        String other = createCustomer();
        long productId = createProduct("10.00");
        inventoryService.setOnHand(productId, 4);
        cartService.add(owner, productId, 2);

        OrderResponse created = checkout(owner, "checkout-success-key");
        assertEquals("PENDING_PAYMENT", created.status());
        assertEquals(new BigDecimal("20.00"), created.total());
        assertEquals(new BigDecimal("10.00"), created.items().getFirst().unitPrice());
        assertEquals("Order item", created.items().getFirst().name());
        assertEquals(created.id(), checkout(owner, "checkout-success-key").id());
        assertThrows(ResourceConflictException.class, () -> orderService.checkout(owner, "second-pending-key"));
        assertCounts(productId, 4, 2, 2);

        Product product = products.findById(productId).orElseThrow();
        product.update(product.getSku(), "Renamed product", product.getDescription(), product.getCategory(),
                new BigDecimal("15.00"));
        products.saveAndFlush(product);
        OrderResponse snapshot = orderService.get(owner, created.id());
        assertEquals("Order item", snapshot.items().getFirst().name());
        assertEquals(new BigDecimal("10.00"), snapshot.items().getFirst().unitPrice());

        OrderResponse paid = orderService.simulatePayment(owner, created.id(), "payment-success-key", PaymentOutcome.SUCCESS);
        assertEquals("CONFIRMED", paid.status());
        assertEquals("SUCCEEDED", paid.paymentStatus());
        assertEquals(0, cartService.get(owner).items().size());
        assertEquals("CONFIRMED", orderService.simulatePayment(owner, created.id(), "payment-success-key",
                PaymentOutcome.SUCCESS).status());
        assertCounts(productId, 2, 0, 2);
        assertThrows(ResourceNotFoundException.class, () -> orderService.get(other, created.id()));
        assertThrows(ResourceConflictException.class, () -> orderService.simulatePayment(owner, created.id(),
                "different-key", PaymentOutcome.FAILURE));
    }

    @Test
    void cancellationAndPaymentTimeoutReleaseReservations() {
        String owner = createCustomer();
        long productId = createProduct("3.00");
        inventoryService.setOnHand(productId, 5);
        cartService.add(owner, productId, 2);

        OrderResponse cancelled = checkout(owner, "cancel-order-key");
        assertCounts(productId, 5, 2, 3);
        assertEquals("CANCELLED", orderService.cancel(owner, cancelled.id()).status());
        assertEquals("CANCELLED", orderService.cancel(owner, cancelled.id()).status());
        assertCounts(productId, 5, 0, 5);

        OrderResponse timedOut = checkout(owner, "timeout-order-key");
        OrderResponse result = orderService.simulatePayment(owner, timedOut.id(), "timeout-payment-key", PaymentOutcome.TIMEOUT);
        assertEquals("CANCELLED", result.status());
        assertEquals("TIMED_OUT", result.paymentStatus());
        assertCounts(productId, 5, 0, 5);
        assertEquals(2, cartService.get(owner).items().getFirst().quantity());
    }

    @Test
    void insufficientStockDoesNotCreateAnOrderOrLeaveReservation() {
        String owner = createCustomer();
        long productId = createProduct("2.00");
        inventoryService.setOnHand(productId, 1);
        cartService.add(owner, productId, 2);

        assertThrows(ResourceConflictException.class, () -> orderService.checkout(owner, "insufficient-key"));
        assertEquals(0L, jdbc.queryForObject("SELECT COUNT(*) FROM orders WHERE user_id = ?", Long.class,
                userIds.getFirst()));
        assertCounts(productId, 1, 0, 1);
    }

    @Test
    void deliveryAdvancesInOrderAndPersistsCustomerNotifications() {
        String owner = createCustomer();
        long productId = createProduct("4.25");
        inventoryService.setOnHand(productId, 2);
        cartService.add(owner, productId, 1);

        OrderResponse pending = checkout(owner, "delivery-checkout-key");
        OrderResponse confirmed = orderService.simulatePayment(owner, pending.id(), "delivery-payment-key",
                PaymentOutcome.SUCCESS);
        assertEquals("PREPARING", confirmed.deliveryStatus());

        DeliveryResponse delivery = deliveryService.forCustomer(owner, pending.id());
        assertEquals(DeliveryStatus.PREPARING.name(), delivery.status());
        Long deliveryId = delivery.id();
        assertThrows(ResourceConflictException.class, () -> deliveryService.update(deliveryId,
                new UpdateDeliveryRequest(DeliveryStatus.PACKED, null, null)));
        delivery = deliveryService.update(delivery.id(),
                new UpdateDeliveryRequest(DeliveryStatus.ASSIGNED, "Courier A", 25));
        assertEquals("Courier A", delivery.courierName());
        delivery = deliveryService.update(delivery.id(),
                new UpdateDeliveryRequest(DeliveryStatus.PACKED, null, null));
        delivery = deliveryService.update(delivery.id(),
                new UpdateDeliveryRequest(DeliveryStatus.OUT_FOR_DELIVERY, null, null));
        delivery = deliveryService.update(delivery.id(),
                new UpdateDeliveryRequest(DeliveryStatus.DELIVERED, null, null));
        assertEquals("DELIVERED", delivery.status());
        assertThrows(ResourceNotFoundException.class, () -> deliveryService.forCustomer("other@example.invalid", pending.id()));

        List<NotificationResponse> notifications = notificationService.list(owner);
        assertEquals(7, notifications.size());
        NotificationResponse unread = notifications.getFirst();
        NotificationResponse read = notificationService.markRead(owner, unread.id());
        assertEquals(unread.id(), read.id());
        assertNotNull(read.readAt());
    }

    private OrderResponse checkout(String email, String key) {
        OrderResponse order = orderService.checkout(email, key);
        orderIds.add(order.id());
        String reservationId = jdbc.queryForObject("SELECT inventory_reservation_id FROM orders WHERE id = ?",
                String.class, order.id());
        reservationIds.add(reservationId);
        return order;
    }

    private void assertCounts(long productId, int onHand, int reserved, int available) {
        InventoryResponse response = inventoryService.get(productId);
        assertEquals(onHand, response.onHand());
        assertEquals(reserved, response.reserved());
        assertEquals(available, response.available());
    }

    private String createCustomer() {
        String email = "order-" + UUID.randomUUID().toString().replace("-", "") + "@example.invalid";
        User user = users.saveAndFlush(new User(email, passwordEncoder.encode("Test-Order-Pass-2026!"), Role.CUSTOMER));
        userIds.add(user.getId());
        return email;
    }

    private long createProduct(String price) {
        String unique = UUID.randomUUID().toString().replace("-", "");
        Category category = categories.saveAndFlush(new Category("Order " + unique, "order-" + unique));
        categoryIds.add(category.getId());
        Product product = products.saveAndFlush(new Product("ORDER-" + unique, "Order item", null, category,
                new BigDecimal(price)));
        productIds.add(product.getId());
        inventoryService.initialize(product);
        return product.getId();
    }
}
