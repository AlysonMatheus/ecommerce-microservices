package com.portfolio.inventory.execption;

import java.util.UUID;

public class StockAlreadyExistsException extends RuntimeException {
    public StockAlreadyExistsException(UUID id) {
        super(" "+id);
    }
}
