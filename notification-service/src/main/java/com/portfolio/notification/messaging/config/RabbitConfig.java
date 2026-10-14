package com.portfolio.notification.messaging.config;

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

    public static final String STOCK_RESERVED_KEY = "stock.reserved";
    public static final String STOCK_REJECTED_KEY = "stock.rejected";

    public static final String STOCK_RESERVED_QUEUE = "notification.stock-reserved.queue";
    public static final String STOCK_REJECTED_QUEUE = "notification.stock-rejected.queue";

    @Bean
    public TopicExchange ordersExchange() {
        return new TopicExchange(ORDERS_EXCHANGE);
    }

    @Bean
    public Queue stockReservedQueue() {
        return QueueBuilder.durable(STOCK_RESERVED_QUEUE).build();
    }

    @Bean
    public Queue stockRejectedQueue() {
        return QueueBuilder.durable(STOCK_REJECTED_QUEUE).build();
    }

    @Bean
    public Binding stockReservedBinding() {
        return BindingBuilder
                .bind(stockReservedQueue())
                .to(ordersExchange())
                .with(STOCK_RESERVED_KEY);
    }

    @Bean
    public Binding stockRejectedBinding() {
        return BindingBuilder
                .bind(stockRejectedQueue())
                .to(ordersExchange())
                .with(STOCK_REJECTED_KEY);
    }

    @Bean
    public Jackson2JsonMessageConverter messageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}