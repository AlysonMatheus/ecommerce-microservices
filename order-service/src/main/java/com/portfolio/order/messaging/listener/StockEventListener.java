package com.portfolio.order.messaging.listener;

import com.portfolio.order.messaging.config.RabbitConfig;
import com.portfolio.order.messaging.event.StockRejectedEvent;
import com.portfolio.order.messaging.event.StockReservedEvent;
import com.portfolio.order.service.OrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class StockEventListener {

    private final OrderService orderService;

    @RabbitListener(queues = RabbitConfig.STOCK_RESERVED_QUEUE)
    public void onStockReserved(StockReservedEvent event) {
        log.info("StockReserved recebido: eventId={}, orderId={}", event.eventId(), event.orderId());
        try {
            orderService.confirm(event.orderId());
        } catch (RuntimeException e) {
            log.error("Pedido {} não encontrado ao confirmar", event.orderId());
        }
    }

    @RabbitListener(queues = RabbitConfig.STOCK_REJECTED_QUEUE)
    public void onStockRejected(StockRejectedEvent event) {
        log.info("StockRejected recebido: eventId={}, orderId={}, motivo={}",
                event.eventId(), event.orderId(), event.reason());
        try {
            orderService.cancel(event.orderId());
        } catch (RuntimeException e) {
            log.error("Pedido {} não encontrado ao cancelar", event.orderId());
        }
    }
}