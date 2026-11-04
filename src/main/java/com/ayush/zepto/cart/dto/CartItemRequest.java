package com.ayush.zepto.cart.dto;

import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.NotNull;

public record CartItemRequest(@NotNull @Positive Integer quantity) {}
