package com.ayush.zepto.delivery.dto;

import com.ayush.zepto.delivery.entity.DeliveryStatus;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UpdateDeliveryRequest(@NotNull DeliveryStatus status,
        @Size(max = 100) String courierName, @Min(1) @Max(240) Integer etaMinutes) {}
