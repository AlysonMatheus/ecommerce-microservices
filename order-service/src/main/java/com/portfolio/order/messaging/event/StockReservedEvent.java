package com.portfolio.order.messaging.event;

import java.time.Instant;
import java.util.UUID;

public record StockReservedEvent(UUID eventId, UUID orderId, Instant occurredAt) {

    public static StockReservedEvent from(UUID orderId) {
        return new StockReservedEvent(UUID.randomUUID(), orderId, Instant.now());
    }
}
