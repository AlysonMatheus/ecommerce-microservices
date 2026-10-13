package com.portfolio.order.dtos.request;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.util.UUID;

public record OrderItemRequest(
        @NotNull UUID productId,
        @NotNull @Positive @Max(1000) Integer quantity,
        @NotNull @Positive @Digits(integer = 10, fraction = 2) BigDecimal unitPrice
) {
}