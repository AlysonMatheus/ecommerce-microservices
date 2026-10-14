package com.portfolio.notification.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Slf4j
@Service
public class NotificationService {

    public void notifyOrderConfirmed(UUID orderId) {
        log.info("NOTIFICAÇÃO: seu pedido {} foi confirmado", orderId);
    }

    public void notifyOrderCancelled(UUID orderId, String reason) {
        log.info("NOTIFICAÇÃO: seu pedido {} foi cancelado. Motivo: {}", orderId, reason);
    }
}