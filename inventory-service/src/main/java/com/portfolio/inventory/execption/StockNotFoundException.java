package com.portfolio.inventory.execption;

import java.util.UUID;

public class StockNotFoundException extends RuntimeException {
    public StockNotFoundException(UUID id) {
        super(" " + id);
    }
}
