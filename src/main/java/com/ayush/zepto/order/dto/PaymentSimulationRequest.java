package com.ayush.zepto.order.dto;

import com.ayush.zepto.order.entity.PaymentOutcome;
import jakarta.validation.constraints.NotNull;

public record PaymentSimulationRequest(@NotNull PaymentOutcome outcome) {}
