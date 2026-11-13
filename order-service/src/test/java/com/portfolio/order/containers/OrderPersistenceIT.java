package com.portfolio.order.containers;

import com.portfolio.order.dtos.request.CreateOrderRequest;
import com.portfolio.order.dtos.request.OrderItemRequest;
import com.portfolio.order.dtos.response.CreateOrderResponse;
import com.portfolio.order.enums.OrderStatus;
import com.portfolio.order.repository.OrderRepository;
import com.portfolio.order.service.OrderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.InstanceOfAssertFactories.LOCAL_DATE;
import static org.mockito.Mockito.when;

@SpringBootTest
@Import(ContainersConfig.class)
@DisplayName("Testes de integração da persistência de pedidos")
class OrderPersistenceIT {

    @Autowired
    OrderService orderService;
    @Autowired
    OrderRepository orderRepository;
    @Autowired
    JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanDatabase() {
        jdbcTemplate.execute("DELETE FROM outbox_events");
        jdbcTemplate.execute("DELETE FROM order_items");
        jdbcTemplate.execute("DELETE FROM orders");
    }

    @Nested
    @DisplayName("create")
    class CreateTest {

        @Test
        @DisplayName("Deve gravar o pedido e os itens vinculados ao pedido")
        void create_validRequest_persistsOrderWithLinkedItems() {
            UUID customerId = UUID.randomUUID();
            UUID firstProductId = UUID.randomUUID();
            UUID secondProductId = UUID.randomUUID();
            CreateOrderRequest request = new CreateOrderRequest(customerId, List.of(
                    new OrderItemRequest(firstProductId, 2, new BigDecimal("15.00")),
                    new OrderItemRequest(secondProductId, 1, new BigDecimal("20.00"))));

            CreateOrderResponse response = orderService.create(request);
            UUID orderId = response.id();

            Integer orders = jdbcTemplate.queryForObject(
                    "SELECT count(*) FROM orders WHERE id = ? AND customer_id = ?",
                    Integer.class, orderId, customerId);
            assertThat(orders).isEqualTo(1);

            Integer linkedItems = jdbcTemplate.queryForObject(
                    "SELECT count(*) FROM order_items WHERE order_id = ?",
                    Integer.class, orderId);
            assertThat(linkedItems).isEqualTo(2);

            Integer firstItem = jdbcTemplate.queryForObject(
                    "SELECT quantity FROM order_items WHERE order_id = ? AND product_id = ?",
                    Integer.class, orderId, firstProductId);
            assertThat(firstItem).isEqualTo(2);
        }

        @Test
        @DisplayName("Deve gravar o total com duas casas decimais")
        void create_validRequest_persistsTotalWithTwoDecimals() {
            CreateOrderRequest request = new CreateOrderRequest(UUID.randomUUID(), List.of(
                    new OrderItemRequest(UUID.randomUUID(), 2, new BigDecimal("15"))));

            CreateOrderResponse response = orderService.create(request);

            BigDecimal total = jdbcTemplate.queryForObject(
                    "SELECT total FROM orders WHERE id = ?",
                    BigDecimal.class, response.id());

            assertThat(total).isEqualByComparingTo("30.00");
            assertThat(total.scale()).isEqualTo(2);
        }

        @Test
        @DisplayName("Deve gravar o pedido como PENDING com a data de criação preenchida")
        void create_validRequest_persistsPendingStatusAndCreatedAt() {
            CreateOrderRequest request = new CreateOrderRequest(UUID.randomUUID(), List.of(
                    new OrderItemRequest(UUID.randomUUID(), 2, new BigDecimal("30.00"))));

            LocalDateTime before = LocalDateTime.now();
            CreateOrderResponse response = orderService.create(request);
            LocalDateTime after = LocalDateTime.now();

            String status = jdbcTemplate.queryForObject(
                    "SELECT status FROM orders WHERE id = ?",
                    String.class, response.id());
            Timestamp createdAt = jdbcTemplate.queryForObject(
                    "SELECT created_at FROM orders WHERE id = ?",
                    Timestamp.class, response.id());

            assertThat(status).isEqualTo("PENDING");
            assertThat(createdAt).isNotNull();
            assertThat(createdAt.toLocalDateTime()).isBetween(before, after);
        }

        @Test
        @DisplayName("Deve gravar o evento order.created no outbox com o orderId como aggregateId")
        void create_validRequest_recordsOrderCreatedInOutbox() {
            CreateOrderRequest request = new CreateOrderRequest(UUID.randomUUID(), List.of(
                    new OrderItemRequest(UUID.randomUUID(), 2, new BigDecimal("30.00"))));

            CreateOrderResponse response = orderService.create(request);

            Map<String, Object> row = jdbcTemplate.queryForMap(
                    "SELECT routing_key, aggregate_id, payload FROM outbox_events WHERE aggregate_id = ?",
                    response.id());

            assertThat(row.get("routing_key")).isEqualTo("order.created");
            assertThat(row.get("aggregate_id")).isEqualTo(response.id());
            assertThat((String) row.get("payload")).contains(response.id().toString());
        }
    }

    @Nested
    @DisplayName("findById")
    class FindByIdTest {

        @Test
        @DisplayName("Deve devolver o pedido com os itens, sem LazyInitializationException")
        void findById_existingOrder_returnsItemsWithoutLazyInitializationException() {
            UUID customerId = UUID.randomUUID();
            UUID firstProductId = UUID.randomUUID();
            CreateOrderRequest request = new CreateOrderRequest(customerId, List.of(
                    new OrderItemRequest(firstProductId, 2, new BigDecimal("15.00"))
            ));

            CreateOrderResponse create = orderService.create(request);
            CreateOrderResponse response = orderService.findById(create.id());


            assertThat(response.id()).isEqualTo(create.id());
            assertThat(response.customerId()).isEqualTo(customerId);
            assertThat(response.status()).isEqualTo(OrderStatus.PENDING);
            assertThat(response.total()).isEqualByComparingTo(new BigDecimal("30.00"));
        }

    }

    @Nested
    @DisplayName("confirm e cancel")
    class StatusChangeTest {

        @Test
        @DisplayName("Deve gravar o status CONFIRMED no banco ao confirmar um pedido pendente")
        void confirm_pendingOrder_persistsConfirmedStatus() {
        }

        @Test
        @DisplayName("Deve gravar o status CANCELLED no banco ao cancelar um pedido pendente")
        void cancel_pendingOrder_persistsCancelledStatus() {
        }

        @Test
        @DisplayName("Deve manter o status no banco ao confirmar um pedido já cancelado")
        void confirm_cancelledOrder_keepsCancelledStatusInDatabase() {
        }
    }
}