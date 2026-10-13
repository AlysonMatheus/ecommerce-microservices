package com.portfolio.order.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.portfolio.order.dtos.request.CreateOrderRequest;
import com.portfolio.order.dtos.response.CreateOrderResponse;
import com.portfolio.order.entity.Order;
import com.portfolio.order.messaging.outbox.OutBoxEvent;
import com.portfolio.order.enums.OrderStatus;
import com.portfolio.order.exception.OrderNotFoundException;
import com.portfolio.order.messaging.config.RabbitConfig;
import com.portfolio.order.messaging.event.OrderCreatedEvent;
import com.portfolio.order.repository.OrderRepository;
import com.portfolio.order.repository.OutBoxEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import com.fasterxml.jackson.core.JsonProcessingException;


import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.UncheckedIOException;
import java.util.List;
import java.util.UUID;


@Slf4j
@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;
    private final OutBoxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;

    @Transactional
    public CreateOrderResponse create(CreateOrderRequest dto) {
        Order order = orderRepository.save(new Order(dto));
      OrderCreatedEvent event = OrderCreatedEvent.from(order);
      outboxEventRepository.save(new OutBoxEvent(
              event.eventId(),
              order.getId(),
              RabbitConfig.ORDER_CREATED_KEY,
              toJson(event)));

        return new CreateOrderResponse(order);
    }
    private String toJson(Object event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException e) {
            throw new UncheckedIOException("Não foi possível serializar o evento", e);
        }
    }

    @Transactional(readOnly = true)
    public CreateOrderResponse findById(UUID id) {
        return new CreateOrderResponse(findOrThrow(id));
    }

    @Transactional(readOnly = true)
    public List<CreateOrderResponse> listAll() {
        return orderRepository.findAll().stream()
                .map(CreateOrderResponse::new)
                .toList();
    }

    @Transactional
    public void confirm(UUID orderId) {
        Order order = findOrThrow(orderId);
        if (order.getStatus() != OrderStatus.PENDING) {
            log.warn("Pedido {} já está {}, ignorando confirmação", orderId, order.getStatus());
            return;
        }
        order.confirm();
    }

    @Transactional
    public void cancel(UUID orderId) {
        Order order = findOrThrow(orderId);
        if (order.getStatus() != OrderStatus.PENDING) {
            log.warn("Pedido {} já está {}, ignorando cancelamento", orderId, order.getStatus());
            return;
        }
        order.cancel();
    }

    private Order findOrThrow(UUID orderId) {
        return orderRepository.findById(orderId)
                .orElseThrow(() -> new OrderNotFoundException(orderId));
    }
}