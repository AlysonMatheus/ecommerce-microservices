package com.portfolio.notification.messaging.listener;

import com.portfolio.notification.messaging.config.RabbitConfig;
import com.portfolio.notification.messaging.event.StockRejectedEvent;
import com.portfolio.notification.messaging.event.StockReservedEvent;
import com.portfolio.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class StockEventListener {

    private final NotificationService notificationService;

    @RabbitListener(queues = RabbitConfig.STOCK_RESERVED_QUEUE)
    public void onStockReserved(StockReservedEvent event) {
        log.info("StockReserved recebido: eventId={}, orderId={}", event.eventId(), event.orderId());
        notificationService.notifyOrderConfirmed(event.orderId());
    }

    @RabbitListener(queues = RabbitConfig.STOCK_REJECTED_QUEUE)
    public void onStockRejected(StockRejectedEvent event) {
        log.info("StockRejected recebido: eventId={}, orderId={}", event.eventId(), event.orderId());
        notificationService.notifyOrderCancelled(event.orderId(), event.reason());
    }
}