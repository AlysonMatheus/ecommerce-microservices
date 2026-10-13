package com.portfolio.order.messaging.event;

import com.portfolio.order.entity.Order;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record OrderCreatedEvent(
        UUID eventId,
        UUID orderId,
        UUID customerId,
        BigDecimal totalAmount,
        List<Item> items,
        Instant occurredAt
) {

    public record Item(UUID productId, Integer quantity) {
    }

    public static OrderCreatedEvent from(Order order) {
        List<Item> items = order.getItems().stream()
                .map(i -> new Item(i.getProductId(), i.getQuantity()))
                .toList();

        return new OrderCreatedEvent(
                UUID.randomUUID(),
                order.getId(),
                order.getCustomerId(),
                order.getTotal(),
                items,
                Instant.now()
        );
    }
}