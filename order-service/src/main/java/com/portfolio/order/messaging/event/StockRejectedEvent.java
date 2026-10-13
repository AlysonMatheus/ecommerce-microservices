package com.portfolio.order.messaging.event;

import java.time.Instant;
import java.util.UUID;

public record StockRejectedEvent(UUID eventId, UUID orderId, String reason, Instant occurredAt) {

    public static StockRejectedEvent from(UUID orderId, String reason) {
        return new StockRejectedEvent(UUID.randomUUID(), orderId, reason, Instant.now());
    }
}