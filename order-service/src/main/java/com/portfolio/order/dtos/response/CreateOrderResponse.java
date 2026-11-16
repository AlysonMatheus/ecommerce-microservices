package com.portfolio.order.dtos.response;

import com.portfolio.order.entity.Order;
import com.portfolio.order.enums.OrderStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record CreateOrderResponse(UUID id, UUID customerId, OrderStatus status,
                                  BigDecimal total, LocalDateTime createdAt,List<OrderItemResponse> orderItems) {
    public CreateOrderResponse(Order order) {
        this(order.getId(), order.getCustomerId(), order.getStatus(),
                order.getTotal(), order.getCreatedAt(),  order.getItems().stream()
                        .map(OrderItemResponse::new)
                        .toList());
    }
}
