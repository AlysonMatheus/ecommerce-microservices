package com.portfolio.inventory.execption;

import java.util.UUID;

public class InsufficientStockException extends RuntimeException {
    public InsufficientStockException(UUID productId, int requested, int available) {
        super("Estoque insuficiente para o produto " + productId
                + ": solicitado " + requested + ", disponível " + available);
    }
}