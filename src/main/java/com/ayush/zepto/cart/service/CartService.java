package com.ayush.zepto.cart.service;

import com.ayush.zepto.cart.dto.CartLineResponse;
import com.ayush.zepto.cart.dto.CartResponse;
import com.ayush.zepto.cart.entity.Cart;
import com.ayush.zepto.cart.entity.CartItem;
import com.ayush.zepto.cart.repository.CartItemRepository;
import com.ayush.zepto.cart.repository.CartRepository;
import com.ayush.zepto.catalog.entity.Product;
import com.ayush.zepto.catalog.repository.ProductRepository;
import com.ayush.zepto.common.error.BadRequestException;
import com.ayush.zepto.common.error.ResourceConflictException;
import com.ayush.zepto.common.error.ResourceNotFoundException;
import com.ayush.zepto.inventory.entity.Inventory;
import com.ayush.zepto.inventory.repository.InventoryRepository;
import com.ayush.zepto.user.User;
import com.ayush.zepto.user.UserRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CartService {
    private final UserRepository users;
    private final CartRepository carts;
    private final CartItemRepository items;
    private final ProductRepository products;
    private final InventoryRepository inventory;

    public CartService(UserRepository users, CartRepository carts, CartItemRepository items,
            ProductRepository products, InventoryRepository inventory) {
        this.users = users;
        this.carts = carts;
        this.items = items;
        this.products = products;
        this.inventory = inventory;
    }

    @Transactional(readOnly = true)
    public CartResponse get(String email) {
        User user = userByEmail(email);
        return carts.findByUser_Id(user.getId()).map(this::toResponse).orElseGet(CartResponse::empty);
    }

    @Transactional
    public CartResponse add(String email, Long productId, int quantity) {
        requirePositiveQuantity(quantity);
        User user = userForUpdate(email);
        Product product = activeProduct(productId);
        Cart cart = lockOrCreateCart(user);
        CartItem item = items.findByCart_IdAndProduct_Id(cart.getId(), productId).orElse(null);
        if (item == null) {
            item = new CartItem(cart, product, quantity);
        } else {
            try {
                item.increaseQuantity(quantity);
            } catch (ArithmeticException exception) {
                throw new BadRequestException("The resulting quantity is too large.");
            }
        }
        items.saveAndFlush(item);
        return toResponse(cart);
    }

    @Transactional
    public CartResponse setQuantity(String email, Long productId, int quantity) {
        requirePositiveQuantity(quantity);
        User user = userForUpdate(email);
        Cart cart = lockExistingCart(user);
        CartItem item = items.findByCart_IdAndProduct_Id(cart.getId(), productId)
                .orElseThrow(() -> new ResourceNotFoundException("Cart item not found."));
        activeProduct(productId);
        item.setQuantity(quantity);
        items.saveAndFlush(item);
        return toResponse(cart);
    }

    @Transactional
    public void remove(String email, Long productId) {
        User user = userForUpdate(email);
        if (carts.findByUser_Id(user.getId()).isEmpty()) return;
        Cart cart = lockExistingCart(user);
        items.deleteByCart_IdAndProduct_Id(cart.getId(), productId);
    }

    private User userByEmail(String email) {
        return users.findByEmail(email).orElseThrow(() -> new ResourceNotFoundException("User account not found."));
    }

    private User userForUpdate(String email) {
        return users.findByEmailForUpdate(email)
                .orElseThrow(() -> new ResourceNotFoundException("User account not found."));
    }

    private Cart lockOrCreateCart(User user) {
        return carts.findByUser_Id(user.getId()).orElseGet(() -> carts.saveAndFlush(new Cart(user)));
    }

    private Cart lockExistingCart(User user) {
        return carts.findByUser_Id(user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Cart not found."));
    }

    private Product activeProduct(Long productId) {
        Product product = products.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found."));
        if (!product.isActive() || !product.getCategory().isActive()) {
            throw new ResourceConflictException("Inactive products cannot be added to or changed in a cart.");
        }
        return product;
    }

    private void requirePositiveQuantity(int quantity) {
        if (quantity < 1) throw new BadRequestException("quantity must be a positive integer.");
    }

    private CartResponse toResponse(Cart cart) {
        List<CartItem> cartItems = items.findAllByCart_IdOrderByIdAsc(cart.getId());
        if (cartItems.isEmpty()) return CartResponse.empty();
        List<Long> productIds = cartItems.stream().map(item -> item.getProduct().getId()).distinct().toList();
        Map<Long, Inventory> stockByProduct = inventory.findAllById(productIds).stream()
                .collect(Collectors.toMap(Inventory::getProductId, Function.identity()));
        BigDecimal subtotal = new BigDecimal("0.00");
        java.util.ArrayList<CartLineResponse> lines = new java.util.ArrayList<>();
        for (CartItem item : cartItems) {
            Product product = item.getProduct();
            BigDecimal lineTotal = product.getPrice().multiply(BigDecimal.valueOf(item.getQuantity()))
                    .setScale(2);
            subtotal = subtotal.add(lineTotal);
            Inventory stock = stockByProduct.get(product.getId());
            int available = stock == null ? 0 : stock.getAvailable();
            boolean active = product.isActive() && product.getCategory().isActive();
            lines.add(new CartLineResponse(product.getId(), product.getSku(), product.getName(), item.getQuantity(),
                    product.getPrice(), lineTotal, active, available));
        }
        return new CartResponse(List.copyOf(lines), subtotal.setScale(2), "INR");
    }
}
