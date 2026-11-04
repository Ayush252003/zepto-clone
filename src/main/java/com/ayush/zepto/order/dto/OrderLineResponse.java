package com.ayush.zepto.order.dto;

import com.ayush.zepto.order.entity.OrderItem;
import java.math.BigDecimal;

public record OrderLineResponse(Long productId, String sku, String name, int quantity,
        BigDecimal unitPrice, BigDecimal lineTotal) {
    public static OrderLineResponse from(OrderItem item) {
        return new OrderLineResponse(item.getProduct().getId(), item.getSkuSnapshot(), item.getNameSnapshot(),
                item.getQuantity(), item.getUnitPrice(), item.getLineTotal());
    }
}
