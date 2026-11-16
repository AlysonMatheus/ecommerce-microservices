package com.portfolio.order.dtos.response;


import com.portfolio.order.entity.OrderItem;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record OrderItemResponse (Long id, UUID productId, Integer quantity, BigDecimal unitPrice){


    public OrderItemResponse(OrderItem item) {
        this(
                item.getId(),
                item.getProductId(),
                item.getQuantity(),
                item.getUnitPrice()
        );
    }
}
