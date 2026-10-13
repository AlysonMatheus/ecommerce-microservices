package com.portfolio.order.messaging.publisher;

import com.portfolio.order.messaging.config.RabbitConfig;

import com.portfolio.order.messaging.event.OrderCreatedEvent;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

@Component
public class OrderEventPublisher {

    private final RabbitTemplate rabbitTemplate;

    public OrderEventPublisher(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    public void publishOrderCreated(OrderCreatedEvent event) {
        rabbitTemplate.convertAndSend(
                RabbitConfig.ORDERS_EXCHANGE,
                RabbitConfig.ORDER_CREATED_KEY,
                event
        );
    }
}
