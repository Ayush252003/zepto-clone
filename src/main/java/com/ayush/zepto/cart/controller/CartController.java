package com.ayush.zepto.cart.controller;

import com.ayush.zepto.cart.dto.CartItemRequest;
import com.ayush.zepto.cart.dto.AddCartItemRequest;
import com.ayush.zepto.cart.dto.CartResponse;
import com.ayush.zepto.cart.service.CartService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/cart")
public class CartController {
    private final CartService cartService;

    public CartController(CartService cartService) { this.cartService = cartService; }

    @GetMapping
    public CartResponse get(@AuthenticationPrincipal Jwt jwt) {
        return cartService.get(jwt.getSubject());
    }

    @PostMapping("/items")
    public CartResponse add(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody AddCartItemRequest request) {
        return cartService.add(jwt.getSubject(), request.productId(), request.quantity());
    }

    @PutMapping("/items/{productId}")
    public CartResponse setQuantity(@AuthenticationPrincipal Jwt jwt, @PathVariable Long productId,
            @Valid @RequestBody CartItemRequest request) {
        return cartService.setQuantity(jwt.getSubject(), productId, request.quantity());
    }

    @DeleteMapping("/items/{productId}")
    public ResponseEntity<Void> remove(@AuthenticationPrincipal Jwt jwt, @PathVariable Long productId) {
        cartService.remove(jwt.getSubject(), productId);
        return ResponseEntity.noContent().build();
    }
}
