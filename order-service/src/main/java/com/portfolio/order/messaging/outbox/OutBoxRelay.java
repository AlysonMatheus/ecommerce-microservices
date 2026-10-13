package com.portfolio.order.messaging.outbox;

import com.portfolio.order.messaging.config.RabbitConfig;
import com.portfolio.order.repository.OutBoxEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class OutBoxRelay {

    private final OutBoxEventRepository repository;
    private final RabbitTemplate rabbitTemplate;

    @Scheduled(fixedDelay = 1000)
    @Transactional
    public void publishPending() {
        List<OutBoxEvent> pending = repository.findTop50ByPublishedAtIsNullOrderByCreatedAtAsc();
        if (pending.isEmpty()) {
            return;
        }

        for (OutBoxEvent event : pending) {
            try {
                Message message = MessageBuilder
                        .withBody(event.getPayload().getBytes(StandardCharsets.UTF_8))
                        .setContentType(MessageProperties.CONTENT_TYPE_JSON)
                        .setContentEncoding("UTF-8")
                        .build();

                rabbitTemplate.send(RabbitConfig.ORDERS_EXCHANGE, event.getRoutingKey(), message);
                event.markPublished();
                log.info("Evento {} publicado ({})", event.getId(), event.getRoutingKey());
            } catch (AmqpException e) {
                log.warn("Falha ao publicar o evento {}, tentando de novo no próximo ciclo", event.getId(), e);
                break;
            }
        }
    }
}