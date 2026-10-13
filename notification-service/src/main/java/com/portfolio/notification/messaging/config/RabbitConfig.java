package com.portfolio.notification.messaging.config;


import org.springframework.amqp.core.*;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitConfig {

    public static final String ORDERS_EXCHANGE = "orders.exchange";

    public static final String ORDER_CREATED_KEY = "order.created";
    public static final String STOCK_RESERVED_KEY = "stock.reserved";
    public static final String STOCK_REJECTED_KEY = "stock.rejected";

    public static final String NOTIFICATION_CREATED_QUEUE = "inventory.order-created.queue";

    @Bean
    public TopicExchange ordersExchange() {
        return new TopicExchange(ORDERS_EXCHANGE);
    }

    @Bean
    public Queue notifcationCreatedQueue() {
        return QueueBuilder.durable(NOTIFICATION_CREATED_QUEUE).build();
    }

    @Bean
    public Binding notificationCreatedBinding(Queue orderCreatedQueue, TopicExchange ordersExchange) {
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