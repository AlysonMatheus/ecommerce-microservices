package com.portfolio.inventory.service;

import com.portfolio.inventory.dtos.request.StockRequest;
import com.portfolio.inventory.entity.ProcessedEvent;
import com.portfolio.inventory.entity.Stock;
import com.portfolio.inventory.execption.StockNotFoundException;
import com.portfolio.inventory.messaging.config.RabbitConfig;
import com.portfolio.inventory.messaging.event.OrderCreatedEvent;
import com.portfolio.inventory.messaging.event.StockRejectedEvent;
import com.portfolio.inventory.messaging.event.StockReservedEvent;
import com.portfolio.inventory.messaging.outbox.OutBoxRecorder;
import com.portfolio.inventory.repository.ProcessedEventRepository;
import com.portfolio.inventory.repository.StockRepository;
import org.hibernate.mapping.Collection;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Testes do StockService")
class StockServiceTest {

    @Mock
    private StockRepository stockRepository;
    @Mock
    private ProcessedEventRepository processedEventRepository;
    @Mock
    private OutBoxRecorder outboxRecorder;

    @InjectMocks
    private StockService stockService;

    private OrderCreatedEvent eventWith(UUID orderId, OrderCreatedEvent.Item... items) {
        return new OrderCreatedEvent(UUID.randomUUID(), orderId, UUID.randomUUID(), BigDecimal.ZERO, List.of(items), Instant.now());
    }

    @Nested
    @DisplayName("handleOrderCreated")
    class HandleOrderCreatedTest {

        @Test
        @DisplayName("Deve reservar o estoque e gravar StockReserved quando todos os itens cabem")
        void handleOrderCreated_allItemsAvailable_reservesStockAndRecordsReserved() {
            UUID productId = UUID.randomUUID();
            UUID orderId = UUID.randomUUID();
            Stock stock = new Stock(new StockRequest(productId, 10));
            OrderCreatedEvent event = eventWith(orderId, new OrderCreatedEvent.Item(productId, 4));

            when(stockRepository.findAllByIdWithLock(anyCollection())).thenReturn(List.of(stock));

            stockService.handleOrderCreated(event);

            assertThat(stock.getAvailable()).isEqualTo(6);
            assertThat(stock.getReserved()).isEqualTo(4);

            ArgumentCaptor<ProcessedEvent> processedCaptor = ArgumentCaptor.forClass(ProcessedEvent.class);
            verify(processedEventRepository).save(processedCaptor.capture());
            assertThat(processedCaptor.getValue().getEventId()).isEqualTo(event.eventId());

            ArgumentCaptor<UUID> eventIdCaptor = ArgumentCaptor.forClass(UUID.class);
            ArgumentCaptor<Object> payloadCaptor = ArgumentCaptor.forClass(Object.class);
            verify(outboxRecorder).record(eventIdCaptor.capture(), eq(orderId), eq(RabbitConfig.STOCK_RESERVED_KEY), payloadCaptor.capture());

            assertThat(payloadCaptor.getValue()).isInstanceOf(StockReservedEvent.class);
            StockReservedEvent reserved = (StockReservedEvent) payloadCaptor.getValue();
            assertThat(reserved.orderId()).isEqualTo(orderId);
        }

        @Test
        @DisplayName("Deve rejeitar e não alterar nenhum estoque quando um item não tem saldo")
        void handleOrderCreated_oneItemWithoutStock_rejectsAndKeepsAllStocksUnchanged() {
            UUID availableProductId = UUID.randomUUID();
            UUID emptyProductId = UUID.randomUUID();
            UUID orderId = UUID.randomUUID();

            Stock withStock = new Stock(new StockRequest(availableProductId, 10));
            Stock withoutStock = new Stock(new StockRequest(emptyProductId, 0));
            OrderCreatedEvent event = eventWith(orderId, new OrderCreatedEvent.Item(availableProductId, 2), new OrderCreatedEvent.Item(emptyProductId, 4));

            when(stockRepository.findAllByIdWithLock(anyCollection())).thenReturn(List.of(withStock, withoutStock));

            stockService.handleOrderCreated(event);

            assertThat(withStock.getAvailable()).as("o item que cabia não pode ser reservado").isEqualTo(10);
            assertThat(withStock.getReserved()).isZero();
            assertThat(withoutStock.getAvailable()).isZero();
            assertThat(withoutStock.getReserved()).isZero();

            ArgumentCaptor<Object> payloadCaptor = ArgumentCaptor.forClass(Object.class);
            verify(outboxRecorder).record(any(UUID.class), eq(orderId), eq(RabbitConfig.STOCK_REJECTED_KEY), payloadCaptor.capture());
            verifyNoMoreInteractions(outboxRecorder);

            assertThat(payloadCaptor.getValue()).isInstanceOf(StockRejectedEvent.class);
            StockRejectedEvent rejected = (StockRejectedEvent) payloadCaptor.getValue();
            assertThat(rejected.orderId()).isEqualTo(orderId);
            assertThat(rejected.reason()).contains(emptyProductId.toString());
        }


        @Test
        @DisplayName("Deve rejeitar com o motivo “Produto não encontrado”")
        void handleOrderCreated_unknownProduct_rejectsWithProductNotFoundReason() {
            UUID orderId = UUID.randomUUID();
            UUID productId = UUID.randomUUID();

            OrderCreatedEvent event = eventWith(orderId, new OrderCreatedEvent.Item(productId, 2));

            when(stockRepository.findAllByIdWithLock(anyCollection())).thenReturn(List.of());

            stockService.handleOrderCreated(event);
            ArgumentCaptor<Object> payloadCaptor = ArgumentCaptor.forClass(Object.class);

            verify(outboxRecorder).record(any(UUID.class), eq(orderId), eq(RabbitConfig.STOCK_REJECTED_KEY), payloadCaptor.capture());

            verifyNoMoreInteractions(outboxRecorder);
            assertThat(payloadCaptor.getValue()).isInstanceOf(StockRejectedEvent.class);
            StockRejectedEvent rejected = (StockRejectedEvent) payloadCaptor.getValue();

            assertThat(rejected.orderId()).isEqualTo(orderId);
            assertThat(rejected.reason()).contains("Produto não encontrado").contains(productId.toString());

        }
    }
}