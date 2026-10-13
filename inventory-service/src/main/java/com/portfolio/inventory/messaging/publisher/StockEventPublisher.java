package com.portfolio.inventory.messaging.publisher;

import com.portfolio.inventory.messaging.config.RabbitConfig;
import com.portfolio.inventory.messaging.event.StockRejectedEvent;
import com.portfolio.inventory.messaging.event.StockReservedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class StockEventPublisher {

    private final RabbitTemplate rabbitTemplate;

    public void publishReserved(StockReservedEvent event) {
        rabbitTemplate.convertAndSend(
                RabbitConfig.ORDERS_EXCHANGE, RabbitConfig.STOCK_RESERVED_KEY, event);
    }

    public void publishRejected(StockRejectedEvent event) {
        rabbitTemplate.convertAndSend(
                RabbitConfig.ORDERS_EXCHANGE, RabbitConfig.STOCK_REJECTED_KEY, event);
    }
}