package com.ayush.zepto.cart.dto;

import java.math.BigDecimal;

public record CartLineResponse(Long productId, String sku, String name, int quantity, BigDecimal unitPrice,
                               BigDecimal lineTotal, boolean active, int availableNow) {}
