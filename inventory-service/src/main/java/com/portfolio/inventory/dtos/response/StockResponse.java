package com.portfolio.inventory.dtos.response;

import com.portfolio.inventory.entity.Stock;

import java.util.UUID;

public record StockResponse(UUID productId, Integer available,Integer reserved) {
    public  StockResponse (Stock stock){
        this(
                stock.getProductId(),
                stock.getAvailable(),
                stock.getReserved()
        );

    }
}
