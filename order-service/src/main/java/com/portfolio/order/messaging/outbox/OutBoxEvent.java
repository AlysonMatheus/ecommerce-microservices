package com.portfolio.order.messaging.outbox;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "outbox_events")
public class OutBoxEvent {

    @Id
    @Column(name = "id")
    private UUID id;

    @Column(name = "aggregate_id", nullable = false)
    private UUID aggregateId;

    @Column(name = "routing_key", nullable = false, length = 100)
    private String routingKey;

    @Column(name = "payload", nullable = false)
    private String payload;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "published_at")
    private LocalDateTime publishedAt;

    public OutBoxEvent(UUID id, UUID aggregateId, String routingKey, String payload) {
        this.id = id;
        this.aggregateId = aggregateId;
        this.routingKey = routingKey;
        this.payload = payload;
    }

    @PrePersist
    void onCreate() {
        this.createdAt = LocalDateTime.now();
    }

    public void markPublished() {
        this.publishedAt = LocalDateTime.now();
    }
}