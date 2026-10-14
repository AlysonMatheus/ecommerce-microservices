package com.portfolio.inventory.messaging.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.portfolio.inventory.repository.OutBoxEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.io.UncheckedIOException;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class OutBoxRecorder {

    private final OutBoxEventRepository repository;
    private final ObjectMapper objectMapper;

    public void record(UUID eventId, UUID aggregateId, String routingKey, Object event) {
        try {
            String payload = objectMapper.writeValueAsString(event);
            repository.save(new OutBoxEvent(eventId, aggregateId, routingKey, payload));
        } catch (JsonProcessingException e) {
            throw new UncheckedIOException("Não foi possível serializar o evento", e);
        }
    }
}