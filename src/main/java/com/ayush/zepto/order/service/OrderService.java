package com.ayush.zepto.order.service;

import com.ayush.zepto.cart.entity.Cart;
import com.ayush.zepto.cart.entity.CartItem;
import com.ayush.zepto.cart.repository.CartItemRepository;
import com.ayush.zepto.cart.repository.CartRepository;
import com.ayush.zepto.catalog.entity.Product;
import com.ayush.zepto.common.error.BadRequestException;
import com.ayush.zepto.common.error.ResourceConflictException;
import com.ayush.zepto.common.error.ResourceNotFoundException;
import com.ayush.zepto.delivery.service.DeliveryService;
import com.ayush.zepto.inventory.service.InventoryService;
import com.ayush.zepto.order.dto.OrderResponse;
import com.ayush.zepto.order.entity.Order;
import com.ayush.zepto.order.entity.OrderItem;
import com.ayush.zepto.order.entity.OrderStatus;
import com.ayush.zepto.order.entity.Payment;
import com.ayush.zepto.order.entity.PaymentOutcome;
import com.ayush.zepto.order.entity.PaymentStatus;
import com.ayush.zepto.order.repository.OrderRepository;
import com.ayush.zepto.order.repository.PaymentRepository;
import com.ayush.zepto.notification.service.NotificationService;
import com.ayush.zepto.user.User;
import com.ayush.zepto.user.UserRepository;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrderService {
    private final UserRepository users;
    private final CartRepository carts;
    private final CartItemRepository cartItems;
    private final OrderRepository orders;
    private final PaymentRepository payments;
    private final InventoryService inventory;
    private final DeliveryService deliveries;
    private final NotificationService notifications;

    public OrderService(UserRepository users, CartRepository carts, CartItemRepository cartItems,
            OrderRepository orders, PaymentRepository payments, InventoryService inventory,
            DeliveryService deliveries, NotificationService notifications) {
        this.users = users;
        this.carts = carts;
        this.cartItems = cartItems;
        this.orders = orders;
        this.payments = payments;
        this.inventory = inventory;
        this.deliveries = deliveries;
        this.notifications = notifications;
    }

    @Transactional
    public OrderResponse checkout(String email, String rawKey) {
        String key = idempotencyKey(rawKey);
        User user = userForUpdate(email);
        var replay = orders.findByUser_IdAndIdempotencyKey(user.getId(), key);
        if (replay.isPresent()) return OrderResponse.from(replay.get());
        if (orders.existsByUser_IdAndStatus(user.getId(), OrderStatus.PENDING_PAYMENT)) {
            throw new ResourceConflictException("A previous order is still awaiting payment.");
        }

        Cart cart = carts.findByUser_Id(user.getId())
                .orElseThrow(() -> new ResourceConflictException("The cart is empty."));
        List<CartItem> lines = cartItems.findAllByCart_IdOrderByProduct_IdAsc(cart.getId());
        if (lines.isEmpty()) throw new ResourceConflictException("The cart is empty.");

        Map<Long, Integer> quantities = new LinkedHashMap<>();
        BigDecimal total = BigDecimal.ZERO.setScale(2);
        for (CartItem line : lines) {
            Product product = line.getProduct();
            if (!product.isActive() || !product.getCategory().isActive()) {
                throw new ResourceConflictException("The cart contains an inactive product.");
            }
            if (line.getQuantity() < 1) throw new ResourceConflictException("The cart contains an invalid quantity.");
            quantities.put(product.getId(), line.getQuantity());
            total = total.add(product.getPrice().multiply(BigDecimal.valueOf(line.getQuantity())).setScale(2));
        }

        String reservationId = UUID.randomUUID().toString();
        inventory.reserve(reservationId, quantities);
        Order order = new Order(user, reservationId, key, total);
        for (CartItem line : lines) {
            order.addItem(new OrderItem(order, line.getProduct(), line.getId(), line.getQuantity(),
                    line.getProduct().getPrice()));
        }
        Order saved = orders.saveAndFlush(order);
        notifications.publish(saved, "ORDER_PLACED", "Your order has been placed and is awaiting payment.");
        return OrderResponse.from(saved);
    }

    @Transactional
    public OrderResponse simulatePayment(String email, Long orderId, String rawKey, PaymentOutcome outcome) {
        String key = idempotencyKey(rawKey);
        if (outcome == null) throw new BadRequestException("A payment outcome is required.");
        User user = userForUpdate(email);
        Order order = ownedOrder(orderId, user.getId());
        Payment existing = order.payment();
        if (existing != null) {
            PaymentStatus expected = paymentStatus(outcome);
            if (existing.getIdempotencyKey().equals(key) && existing.getStatus() == expected) {
                return OrderResponse.from(order);
            }
            throw new ResourceConflictException("This order already has a completed simulated payment.");
        }
        if (order.getStatus() != OrderStatus.PENDING_PAYMENT) {
            throw new ResourceConflictException("Only an order awaiting payment can be paid.");
        }

        PaymentStatus status = paymentStatus(outcome);
        Payment payment = new Payment(order, key, status);
        order.addPayment(payment);
        if (outcome == PaymentOutcome.SUCCESS) {
            inventory.commit(order.getInventoryReservationId());
            order.confirm();
            order.getItems().stream().filter(item -> item.getCartItemId() != null)
                    .forEach(item -> cartItems.deleteByIdAndQuantity(item.getCartItemId(), item.getQuantity()));
            deliveries.createFor(order);
            notifications.publish(order, "PAYMENT_SUCCEEDED", "Payment succeeded and your order is confirmed.");
        } else {
            inventory.release(order.getInventoryReservationId());
            order.cancel();
            notifications.publish(order, "ORDER_CANCELLED", "Payment did not complete; your order was cancelled.");
        }
        orders.saveAndFlush(order);
        payments.saveAndFlush(payment);
        return OrderResponse.from(order);
    }

    @Transactional
    public OrderResponse cancel(String email, Long orderId) {
        User user = userForUpdate(email);
        Order order = ownedOrder(orderId, user.getId());
        if (order.getStatus() == OrderStatus.CANCELLED) return OrderResponse.from(order);
        if (order.getStatus() != OrderStatus.PENDING_PAYMENT) {
            throw new ResourceConflictException("Only an order awaiting payment can be cancelled.");
        }
        inventory.release(order.getInventoryReservationId());
        order.cancel();
        notifications.publish(order, "ORDER_CANCELLED", "Your order was cancelled and reserved stock was released.");
        orders.saveAndFlush(order);
        return OrderResponse.from(order);
    }

    @Transactional(readOnly = true)
    public OrderResponse get(String email, Long orderId) {
        User user = user(email);
        return OrderResponse.from(ownedOrder(orderId, user.getId()));
    }

    @Transactional(readOnly = true)
    public List<OrderResponse> history(String email) {
        User user = user(email);
        return orders.findAllByUser_IdOrderByCreatedAtDesc(user.getId()).stream().map(OrderResponse::from).toList();
    }

    private User user(String email) {
        return users.findByEmail(email).orElseThrow(() -> new ResourceNotFoundException("User account not found."));
    }

    private User userForUpdate(String email) {
        return users.findByEmailForUpdate(email)
                .orElseThrow(() -> new ResourceNotFoundException("User account not found."));
    }

    private Order ownedOrder(Long id, Long userId) {
        return orders.findByIdAndUser_Id(id, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found."));
    }

    private String idempotencyKey(String rawKey) {
        if (rawKey == null || rawKey.isBlank() || rawKey.length() > 100) {
            throw new BadRequestException("Idempotency-Key must contain 1 to 100 characters.");
        }
        return rawKey;
    }

    private PaymentStatus paymentStatus(PaymentOutcome outcome) {
        return switch (outcome) {
            case SUCCESS -> PaymentStatus.SUCCEEDED;
            case FAILURE -> PaymentStatus.FAILED;
            case TIMEOUT -> PaymentStatus.TIMED_OUT;
        };
    }
}
