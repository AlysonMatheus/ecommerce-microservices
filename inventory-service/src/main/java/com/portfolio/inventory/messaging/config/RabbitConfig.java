package com.portfolio.inventory.messaging.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitConfig {

    public static final String ORDERS_EXCHANGE = "orders.exchange";

    public static final String ORDER_CREATED_KEY = "order.created";
    public static final String STOCK_RESERVED_KEY = "stock.reserved";
    public static final String STOCK_REJECTED_KEY = "stock.rejected";

    public static final String ORDER_CREATED_QUEUE = "inventory.order-created.queue";

    @Bean
    public TopicExchange ordersExchange() {
        return new TopicExchange(ORDERS_EXCHANGE);
    }

    @Bean
    public Queue orderCreatedQueue() {
        return QueueBuilder.durable(ORDER_CREATED_QUEUE).build();
    }

    @Bean
    public Binding orderCreatedBinding(Queue orderCreatedQueue, TopicExchange ordersExchange) {
        return BindingBuilder
                .bind(orderCreatedQueue)
                .to(ordersExchange)
                .with(ORDER_CREATED_KEY);
    }

    @Bean
    public Jackson2JsonMessageConverter messageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}