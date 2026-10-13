package com.portfolio.inventory.messaging.listener;

import com.portfolio.inventory.messaging.config.RabbitConfig;
import com.portfolio.inventory.messaging.event.OrderCreatedEvent;
import com.portfolio.inventory.service.StockService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderEventListener {

    private final StockService stockService;

    @RabbitListener(queues = RabbitConfig.ORDER_CREATED_QUEUE)
    public void onOrderCreated(OrderCreatedEvent event) {
        log.info("OrderCreated recebido: eventId={}, orderId={}", event.eventId(), event.orderId());
        try {
            stockService.handleOrderCreated(event);
        } catch (DataIntegrityViolationException e) {
            log.warn("Evento {} já processado, ignorando", event.eventId());
        }
    }
}