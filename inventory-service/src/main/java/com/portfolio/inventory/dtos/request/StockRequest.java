package com.portfolio.inventory.dtos.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.util.UUID;

public record StockRequest(
        @NotNull UUID productId,
        @NotNull @PositiveOrZero Integer available
) {
}