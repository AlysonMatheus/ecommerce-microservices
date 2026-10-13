package com.portfolio.inventory.messaging.outbox;

import java.time.LocalDateTime;

public record OutBoxEventRequest(String routing_key, String payload, LocalDateTime created_at, LocalDateTime published_at) {

}
