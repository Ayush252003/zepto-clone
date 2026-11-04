package com.ayush.zepto.cart.dto;

import java.math.BigDecimal;
import java.util.List;

public record CartResponse(List<CartLineResponse> items, BigDecimal subtotal, String currency) {
    public static CartResponse empty() {
        return new CartResponse(List.of(), new BigDecimal("0.00"), "INR");
    }
}
