package com.portfolio.inventory.containers;


import com.portfolio.inventory.entity.Stock;
import com.portfolio.inventory.messaging.config.RabbitConfig;
import com.portfolio.inventory.messaging.event.OrderCreatedEvent;
import com.portfolio.inventory.service.StockService;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.stream.IntStream;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

@SpringBootTest
@Import(ContainersConfig.class)
@DisplayName("Testes de integração do StockService")
class StockServiceIT {

    @Autowired
    private StockService stockService;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    // @Autowired StockService e JdbcTemplate.
    // @BeforeEach limpa as tabelas: outbox_events, processed_events e stock.
    // Sem @Transactional na classe: o commit precisa acontecer de verdade.
    // Um helper para cadastrar estoque e outro para montar o OrderCreatedEvent.

    @BeforeEach
    void cleanDatabase() {
        jdbcTemplate.execute("DELETE FROM outbox_events");
        jdbcTemplate.execute("DELETE FROM processed_events");
        jdbcTemplate.execute("DELETE FROM stock");
    }

    private void insertStock(UUID productId, int available) {
        jdbcTemplate.update(
                "INSERT INTO stock (product_id, available, reserved) VALUES (?, ?, 0)",
                productId, available);
    }

    private OrderCreatedEvent eventWith(UUID orderId, OrderCreatedEvent.Item... items) {
        return new OrderCreatedEvent(UUID.randomUUID(), orderId, UUID.randomUUID(),
                BigDecimal.ZERO, List.of(items), Instant.now());
    }


    @Test
    @DisplayName("Deve gravar a reserva no banco quando todos os itens cabem")
    void handleOrderCreated_allItemsAvailable_persistsReservedStock() {
        UUID productId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        insertStock(productId, 10);

        OrderCreatedEvent event = eventWith(orderId, new OrderCreatedEvent.Item(productId, 4));

        stockService.handleOrderCreated(event);

        Integer available = jdbcTemplate.queryForObject(
                "SELECT available FROM stock WHERE product_id = ?", Integer.class, productId);

        assertThat(available).isEqualTo(6);

        Integer reserved = jdbcTemplate.queryForObject(
                "SELECT reserved FROM stock WHERE product_id = ?",
                Integer.class,
                productId);

        assertThat(reserved).isEqualTo(4);

        Integer processedEvents = jdbcTemplate.queryForObject(
                """
                        SELECT COUNT(*)
                        FROM processed_events
                        WHERE event_id = ?
                        """,
                Integer.class,
                event.eventId());

        assertThat(processedEvents).isEqualTo(1);

        Integer outboxEvents = jdbcTemplate.queryForObject(
                """
                        SELECT COUNT(*)
                        FROM outbox_events
                        WHERE aggregate_id = ?
                          AND routing_key = ?
                        """,
                Integer.class,
                orderId,
                "stock.reserved"
        );
        assertThat(outboxEvents).isEqualTo(1);
    }

    @Test
    @DisplayName("Deve gravar stock.reserved no outbox com o orderId como aggregateId")
    void handleOrderCreated_allItemsAvailable_recordsStockReservedInOutbox() {
        UUID productId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        insertStock(productId, 10);
        OrderCreatedEvent event = eventWith(orderId,
                new OrderCreatedEvent.Item(productId, 4));


        stockService.handleOrderCreated(event);

        Map<String, Object> row = jdbcTemplate.queryForMap(
                "SELECT routing_key, aggregate_id, payload FROM outbox_events WHERE aggregate_id = ?",
                orderId);

        assertThat(row.get("routing_key")).isEqualTo(RabbitConfig.STOCK_RESERVED_KEY);
        assertThat(row.get("aggregate_id")).isEqualTo(orderId);
        assertThat((String) row.get("payload")).contains(orderId.toString());
    }

    @Test
    @DisplayName("Deve registrar o eventId em processed_events")
    void handleOrderCreated_allItemsAvailable_savesProcessedEvent() {
        UUID productId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        insertStock(productId, 10);
        OrderCreatedEvent event = eventWith(orderId, new OrderCreatedEvent.Item(productId, 4));

        stockService.handleOrderCreated(event);

        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM processed_events WHERE event_id =? "
                , Integer.class, event.eventId());
        assertThat(count).isEqualTo(1);


    }

    @Test
    @DisplayName("Deve manter todos os estoques quando um item não tem saldo")
    void handleOrderCreated_oneItemWithoutStock_keepsAllStocksUnchangedInDatabase() {
        UUID productId1 = UUID.randomUUID();
        UUID productId2 = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();

        insertStock(productId1, 10);
        insertStock(productId2, 0);

        OrderCreatedEvent event = eventWith(orderId,
                new OrderCreatedEvent.Item(productId1, 4),
                new OrderCreatedEvent.Item(productId2, 4));

        stockService.handleOrderCreated(event);

        Map<String, Object> stock1 = jdbcTemplate.queryForMap("SELECT available, reserved FROM stock WHERE product_id = ?", productId1);
        Map<String, Object> stock2 = jdbcTemplate.queryForMap("SELECT available, reserved FROM stock WHERE product_id = ?", productId2);

        assertThat(stock1.get("available")).isEqualTo(10);
        assertThat(stock1.get("reserved")).isEqualTo(0);
        assertThat(stock2.get("available")).isEqualTo(0);
        assertThat(stock2.get("reserved")).isEqualTo(0);
    }

    @Test
    @DisplayName("Deve gravar stock.rejected no outbox com o motivo da rejeição")
    void handleOrderCreated_oneItemWithoutStock_recordsStockRejectedInOutbox() {
        UUID productId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        insertStock(productId, 2);
        OrderCreatedEvent event = eventWith(orderId, new OrderCreatedEvent.Item(productId, 4));

        stockService.handleOrderCreated(event);

        Map<String, Object> row = jdbcTemplate.queryForMap(
                "SELECT routing_key, payload FROM outbox_events WHERE aggregate_id = ?",
                orderId);
        String payload = (String) row.get("payload");

        assertThat(row.get("routing_key")).isEqualTo(RabbitConfig.STOCK_REJECTED_KEY);
        assertThat(payload)
                .contains(orderId.toString())
                .contains("Estoque insuficiente")
                .contains(productId.toString());
    }


    @Nested
    @DisplayName("handleOrderCreated: idempotência")
    class IdempotencyTest {

        @Test
        @DisplayName("Deve reservar uma única vez quando o mesmo eventId é entregue duas vezes")
        void handleOrderCreated_sameEventTwice_reservesOnlyOnce() {
            UUID productId = UUID.randomUUID();
            UUID orderId = UUID.randomUUID();
            insertStock(productId, 6);
            OrderCreatedEvent event = eventWith(orderId, new OrderCreatedEvent.Item(productId, 4));
            stockService.handleOrderCreated(event);
            stockService.handleOrderCreated(event);

            Map<String, Object> row = jdbcTemplate.queryForMap(
                    "SELECT available, reserved FROM stock WHERE product_id = ?",
                    productId
            );
            assertThat(row.get("available")).isEqualTo(2);
            assertThat(row.get("reserved")).isEqualTo(4);


        }

        @Test
        @DisplayName("Deve gravar um único processed_events e um único evento no outbox")
        void handleOrderCreated_sameEventTwice_recordsOneProcessedEventAndOneOutboxEvent() {
            UUID productId = UUID.randomUUID();
            UUID orderId = UUID.randomUUID();
            insertStock(productId, 6);
            OrderCreatedEvent event = eventWith(orderId, new OrderCreatedEvent.Item(productId, 4));
            stockService.handleOrderCreated(event);
            stockService.handleOrderCreated(event);
            Integer processedEvents = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM processed_events WHERE event_id = ?  ", Integer.class, event.eventId());

            Integer outboxEvents = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM outbox_events WHERE aggregate_id = ? AND routing_key =? ", Integer.class, orderId, "stock.reserved");

            assertThat(processedEvents).isEqualTo(1);
            assertThat(outboxEvents).isEqualTo(1);
            Map<String, Object> stock = jdbcTemplate.queryForMap(
                    "SELECT available, reserved FROM stock WHERE product_id = ?", productId);

            assertThat(stock.get("available")).isEqualTo(2);
            assertThat(stock.get("reserved")).isEqualTo(4);

        }
    }

    @Nested
    @DisplayName("handleOrderCreated: concorrência")
    class ConcurrencyTest {

        @Test
        @DisplayName("Nunca deve reservar mais unidades do que o saldo com pedidos simultâneos")
        void handleOrderCreated_concurrentOrders_neverReservesMoreThanAvailable() throws Exception {
            int initialStock = 5;
            int threads = 10;
            UUID productId = UUID.randomUUID();
            insertStock(productId, initialStock);

            ExecutorService executor = Executors.newFixedThreadPool(threads);
            CountDownLatch ready = new CountDownLatch(threads);
            CountDownLatch start = new CountDownLatch(1);
            List<Future<Object>> futures = new ArrayList<>();

            for (int i = 0; i < threads; i++) {
                OrderCreatedEvent event = eventWith(
                        UUID.randomUUID(), new OrderCreatedEvent.Item(productId, 1));

                futures.add(executor.submit(() -> {
                    ready.countDown();
                    start.await();
                    stockService.handleOrderCreated(event);
                    return null;
                }));
            }

            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            for (Future<Object> future : futures) {
            }
            executor.shutdown();

            Map<String, Object> row = jdbcTemplate.queryForMap(
                    "SELECT available, reserved FROM stock WHERE product_id = ?", productId);

            assertThat(row.get("reserved")).isEqualTo(initialStock);
            assertThat(row.get("available")).isEqualTo(0);
        }

        @Test
        @DisplayName("Deve gravar um resultado no outbox para cada pedido simultâneo")
        void handleOrderCreated_concurrentOrders_recordsOneOutcomePerOrder() throws Exception {
            int initialStock = 5;
            int threads = 10;
            UUID productId = UUID.randomUUID();
            insertStock(productId, initialStock);

            List<OrderCreatedEvent> events = IntStream.range(0, threads)
                    .mapToObj(i -> eventWith(UUID.randomUUID(), new OrderCreatedEvent.Item(productId, 1)))
                    .toList();

            ExecutorService executor = Executors.newFixedThreadPool(threads);
            CountDownLatch ready = new CountDownLatch(threads);
            CountDownLatch start = new CountDownLatch(1);
            List<Future<?>> futures = new ArrayList<>();

            for (OrderCreatedEvent event : events) {
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    start.await();
                    stockService.handleOrderCreated(event);
                    return null;
                }));
            }

            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            for (Future<?> future : futures) {
                future.get(30, TimeUnit.SECONDS);
            }
            executor.shutdown();

            Integer reservedEvents = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM outbox_events WHERE routing_key = ?",
                    Integer.class, RabbitConfig.STOCK_RESERVED_KEY);
            Integer rejectedEvents = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM outbox_events WHERE routing_key = ?",
                    Integer.class, RabbitConfig.STOCK_REJECTED_KEY);
            Integer distinctOrders = jdbcTemplate.queryForObject(
                    "SELECT COUNT(DISTINCT aggregate_id) FROM outbox_events", Integer.class);

            assertThat(reservedEvents).isEqualTo(initialStock);
            assertThat(rejectedEvents).isEqualTo(threads - initialStock);
            assertThat(distinctOrders).isEqualTo(threads);
        }

        @Test
        @DisplayName("Não deve gerar deadlock quando pedidos simultâneos dividem produtos em ordem diferente")
        void handleOrderCreated_concurrentOrdersWithSharedProducts_doesNotDeadlock() throws Exception {
            int initialStock = 10;
            UUID productId1 = UUID.randomUUID();
            UUID productId2 = UUID.randomUUID();

            insertStock(productId1, initialStock);
            insertStock(productId2, initialStock);

            OrderCreatedEvent event1 = eventWith(UUID.randomUUID(),
                    new OrderCreatedEvent.Item(productId1, 2),
                    new OrderCreatedEvent.Item(productId2, 3));

            OrderCreatedEvent event2 = eventWith(UUID.randomUUID(),
                    new OrderCreatedEvent.Item(productId2, 3),
                    new OrderCreatedEvent.Item(productId1, 2));

            int threads = 2;
            ExecutorService executor = Executors.newFixedThreadPool(threads);
            CountDownLatch ready = new CountDownLatch(threads);
            CountDownLatch start = new CountDownLatch(1);
            List<Future<?>> futures = new ArrayList<>();
            List<OrderCreatedEvent> events = List.of(event1, event2);

            for (OrderCreatedEvent event : events) {
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    start.await();
                    stockService.handleOrderCreated(event);
                    return null;
                }));
            }
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            for (Future<?> future : futures) {
                future.get(30, TimeUnit.SECONDS);
            }
            executor.shutdown();


        }
    }

    @Nested
    @DisplayName("handleOrderCreated: atomicidade")
    class AtomicityTest {

        @Test
        @DisplayName("Deve desfazer a reserva quando a gravação do outbox falha")
        void handleOrderCreated_outboxFails_rollsBackReservation() {
        }
    }

    @Nested
    @DisplayName("create")
    class CreateTest {

        @Test
        @DisplayName("Deve gravar o novo produto com reserved zerado")
        void create_newProduct_persistsStockWithZeroReserved() {
        }

        @Test
        @DisplayName("Deve lançar StockAlreadyExistsException e preservar o estoque existente")
        void create_existingProduct_throwsAndKeepsExistingStock() {
        }
    }
}