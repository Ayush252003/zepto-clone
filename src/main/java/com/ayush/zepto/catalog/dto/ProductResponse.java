package com.ayush.zepto.catalog.dto;

import java.math.BigDecimal;

public record ProductResponse(
        Long id,
        String sku,
        String name,
        String description,
        CategorySummary category,
        BigDecimal price,
        String currency,
        boolean active) implements java.io.Serializable {
}
