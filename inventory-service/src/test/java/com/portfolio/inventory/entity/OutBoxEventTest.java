package com.portfolio.inventory.entity;

import com.portfolio.inventory.messaging.outbox.OutBoxEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.UUID;

public class OutBoxEventTest {
    @Test
    @DisplayName("Deve inicializar evento com os dados informados")
    void constructor_validData_initializesEvent() {

        UUID id = UUID.randomUUID();
        UUID aggregateId = UUID.randomUUID();

        OutBoxEvent boxEvent = new OutBoxEvent(
                id,
                aggregateId,
                "order.created",
                "{}"
        );

        assertThat(boxEvent.getId()).isEqualTo(id);
        assertThat(boxEvent.getAggregateId()).isEqualTo(aggregateId);
        assertThat(boxEvent.getRoutingKey()).isEqualTo("order.created");
        assertThat(boxEvent.getPayload()).isEqualTo("{}");
        assertThat(boxEvent.getPublishedAt()).isNull();
    }

    @Test
    @DisplayName("Deve registrar a data de publicação do evento")
    void markPublished_shouldSetPublishedAt() {

        OutBoxEvent boxEvent = new OutBoxEvent(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "order.created",
                "{}"
        );

        LocalDateTime before = LocalDateTime.now();

        boxEvent.markPublished();

        LocalDateTime after = LocalDateTime.now();

        assertThat(boxEvent.getPublishedAt())
                .isNotNull()
                .isBetween(before, after);
    }

}
