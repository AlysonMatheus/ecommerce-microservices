package com.portfolio.notification.messaging.event;


import java.time.Instant;
import java.util.UUID;




public record StockReservedEvent(UUID eventId, UUID orderId, Instant occurredAt) {
}