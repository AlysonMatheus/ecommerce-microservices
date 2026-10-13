package com.portfolio.order.dtos.response;


import java.math.BigDecimal;
import java.util.UUID;

public record OrderItemResponse (Long id, UUID productId, Integer quantity, BigDecimal unitPrice){
}
