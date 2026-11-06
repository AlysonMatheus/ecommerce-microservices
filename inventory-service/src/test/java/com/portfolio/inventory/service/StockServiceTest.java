package com.portfolio.inventory.service;

import com.portfolio.inventory.dtos.request.StockRequest;
import com.portfolio.inventory.dtos.response.StockResponse;
import com.portfolio.inventory.entity.ProcessedEvent;
import com.portfolio.inventory.entity.Stock;
import com.portfolio.inventory.execption.StockAlreadyExistsException;
import com.portfolio.inventory.execption.StockNotFoundException;
import com.portfolio.inventory.messaging.config.RabbitConfig;
import com.portfolio.inventory.messaging.event.OrderCreatedEvent;
import com.portfolio.inventory.messaging.event.StockRejectedEvent;
import com.portfolio.inventory.messaging.event.StockReservedEvent;
import com.portfolio.inventory.messaging.outbox.OutBoxRecorder;
import com.portfolio.inventory.repository.ProcessedEventRepository;
import com.portfolio.inventory.repository.StockRepository;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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

        @Test
        @DisplayName("Não deve reservar, salvar nem gravar evento quando o eventId já foi processado")
        void handleOrderCreated_alreadyProcessedEvent_doesNothing() {
            UUID productId = UUID.randomUUID();
            UUID orderId = UUID.randomUUID();

            Stock stock = new Stock(new StockRequest(productId, 10));
            OrderCreatedEvent event = eventWith(orderId, new OrderCreatedEvent.Item(productId, 4));

            when(stockRepository.findAllByIdWithLock(anyCollection())).thenReturn(List.of(stock));
            when(processedEventRepository.existsById(event.eventId())).thenReturn(true);

            stockService.handleOrderCreated(event);

            assertThat(stock.getAvailable()).as("o estoque não pode ser reservado de novo").isEqualTo(10);
            assertThat(stock.getReserved()).isZero();

            verify(processedEventRepository, never()).save(any());
            verifyNoInteractions(outboxRecorder);
        }

        @Test
        @DisplayName("Deve rejeitar quando a soma do mesmo produto em duas linhas excede o saldo")
        void handleOrderCreated_sameProductTwice_sumExceedsStock_rejects() {
            UUID productId = UUID.randomUUID();
            UUID orderId = UUID.randomUUID();

            Stock stock = new Stock(new StockRequest(productId, 10));
            OrderCreatedEvent event = eventWith(orderId, new OrderCreatedEvent.Item(productId, 4), new OrderCreatedEvent.Item(productId, 7));

            when(stockRepository.findAllByIdWithLock(anyCollection())).thenReturn(List.of(stock));

            stockService.handleOrderCreated(event);

            assertThat(stock.getAvailable()).as("cada linha sozinha cabia, mas a soma não").isEqualTo(10);
            assertThat(stock.getReserved()).isZero();

            ArgumentCaptor<Object> payloadCaptor = ArgumentCaptor.forClass(Object.class);
            verify(outboxRecorder).record(any(UUID.class), eq(orderId), eq(RabbitConfig.STOCK_REJECTED_KEY), payloadCaptor.capture());
            verifyNoMoreInteractions(outboxRecorder);

            assertThat(payloadCaptor.getValue()).isInstanceOf(StockRejectedEvent.class);
            StockRejectedEvent rejected = (StockRejectedEvent) payloadCaptor.getValue();
            assertThat(rejected.reason()).contains(productId.toString());
        }

        @Test
        @DisplayName("Deve reservar a soma quando o mesmo produto aparece em duas linhas")
        void handleOrderCreated_sameProductTwice_sumWithinStock_reservesTotal() {
            UUID productId = UUID.randomUUID();
            UUID orderId = UUID.randomUUID();

            Stock stock = new Stock(new StockRequest(productId, 10));
            OrderCreatedEvent event = eventWith(orderId,
                    new OrderCreatedEvent.Item(productId, 4),
                    new OrderCreatedEvent.Item(productId, 6));

            when(stockRepository.findAllByIdWithLock(anyCollection())).thenReturn(List.of(stock));

            stockService.handleOrderCreated(event);

            assertThat(stock.getAvailable()).isZero();
            assertThat(stock.getReserved()).isEqualTo(10);

            ArgumentCaptor<Object> payloadCaptor = ArgumentCaptor.forClass(Object.class);
            verify(outboxRecorder).record(any(UUID.class), eq(orderId), eq(RabbitConfig.STOCK_RESERVED_KEY), payloadCaptor.capture());
            verifyNoMoreInteractions(outboxRecorder);

            assertThat(payloadCaptor.getValue()).isInstanceOf(StockReservedEvent.class);
        }


        @Test
        @DisplayName("Deve gerar um eventId novo e usar o orderId como aggregateId")
        void handleOrderCreated_anyOutcome_usesNewEventIdAndOrderIdAsAggregate() {
            UUID productId = UUID.randomUUID();
            UUID orderId = UUID.randomUUID();
            Stock stock = new Stock(new StockRequest(productId, 10));
            OrderCreatedEvent event = eventWith(orderId, new OrderCreatedEvent.Item(productId, 4));

            when(stockRepository.findAllByIdWithLock(anyCollection())).thenReturn(List.of(stock));

            stockService.handleOrderCreated(event);

            ArgumentCaptor<UUID> eventIdCaptor = ArgumentCaptor.forClass(UUID.class);
            verify(outboxRecorder).record(eventIdCaptor.capture(), eq(orderId), any(String.class), any());

            assertThat(eventIdCaptor.getValue()).isNotEqualTo(event.eventId());
        }


        @Test
        @DisplayName("Deve registrar o evento como processado também na rejeição")
        void handleOrderCreated_rejection_stillSavesProcessedEvent() {
            UUID productId = UUID.randomUUID();
            UUID orderId = UUID.randomUUID();
            Stock stock = new Stock(new StockRequest(productId, 2));
            OrderCreatedEvent event = eventWith(orderId, new OrderCreatedEvent.Item(productId, 4));

            when(stockRepository.findAllByIdWithLock(anyCollection())).thenReturn(List.of(stock));

            stockService.handleOrderCreated(event);

            verify(outboxRecorder).record(any(UUID.class), eq(orderId), eq(RabbitConfig.STOCK_REJECTED_KEY), any());

            ArgumentCaptor<ProcessedEvent> processedCaptor = ArgumentCaptor.forClass(ProcessedEvent.class);
            verify(processedEventRepository).save(processedCaptor.capture());
            assertThat(processedCaptor.getValue().getEventId()).isEqualTo(event.eventId());
        }

        @Test
        @DisplayName("Deve reservar cada produto quando um pedido com vários produtos cabe")
        void handleOrderCreated_multipleProducts_allAvailable_reservesEach() {
            UUID firstId = UUID.randomUUID();
            UUID secondId = UUID.randomUUID();
            UUID orderId = UUID.randomUUID();

            Stock first = new Stock(new StockRequest(firstId, 10));
            Stock second = new Stock(new StockRequest(secondId, 5));
            OrderCreatedEvent event = eventWith(orderId,
                    new OrderCreatedEvent.Item(firstId, 2),
                    new OrderCreatedEvent.Item(secondId, 3));

            when(stockRepository.findAllByIdWithLock(anyCollection())).thenReturn(List.of(first, second));

            stockService.handleOrderCreated(event);

            assertThat(first.getAvailable()).isEqualTo(8);
            assertThat(first.getReserved()).isEqualTo(2);
            assertThat(second.getAvailable()).isEqualTo(2);
            assertThat(second.getReserved()).isEqualTo(3);
        }
    }


    @Nested
    @DisplayName("create")
    class CreateTest {

        @Test
        @DisplayName("Deve salvar o novo produto e devolver o response")
        void create_newProduct_savesAndReturnsResponse() {
            UUID productId = UUID.randomUUID();
            StockRequest request = new StockRequest(productId, 10);

            when(stockRepository.save(any(Stock.class))).thenAnswer(invocation -> invocation.getArgument(0));

            StockResponse response = stockService.create(request);

            ArgumentCaptor<Stock> stockCaptor = ArgumentCaptor.forClass(Stock.class);
            verify(stockRepository).save(stockCaptor.capture());
            assertThat(stockCaptor.getValue().getProductId()).isEqualTo(productId);
            assertThat(stockCaptor.getValue().getAvailable()).isEqualTo(10);
            assertThat(stockCaptor.getValue().getReserved()).isZero();

            assertThat(response.productId()).isEqualTo(productId);
            assertThat(response.available()).isEqualTo(10);
            assertThat(response.reserved()).isZero();
        }

        @Test
        @DisplayName("Deve lançar StockAlreadyExistsException sem salvar")
        void create_existingProduct_throwsAndDoesNotSave() {
            UUID productId = UUID.randomUUID();
            StockRequest request = new StockRequest(productId, 10);

            when(stockRepository.existsById(productId)).thenReturn(true);

            assertThatThrownBy(() -> stockService.create(request))
                    .isInstanceOf(StockAlreadyExistsException.class);

            verify(stockRepository, never()).save(any());
        }


    }

    @Nested
    @DisplayName("findById")
    class FindByIdTest {

        @Test
        @DisplayName("Deve devolver o estoque encontrado")
        void findById_existingProduct_returnsResponse() {
            UUID productId = UUID.randomUUID();
            Stock stock = new Stock(new StockRequest(productId, 10));
            stock.reserve(3);
            when(stockRepository.findById(productId)).thenReturn(Optional.of(stock));

            StockResponse response = stockService.findById(productId);
            assertThat(response.productId()).isEqualTo(productId);
            assertThat(response.available()).isEqualTo(7);
            assertThat(response.reserved()).isEqualTo(3);
        }

        @Test
        @DisplayName("Deve lançar StockNotFoundException quando o produto não existe")
        void findById_unknownProduct_throwsNotFound() {
            UUID productId = UUID.randomUUID();

            when(stockRepository.findById(productId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> stockService.findById(productId))
                    .isInstanceOf(StockNotFoundException.class)
                    .hasMessageContaining(productId.toString());
        }
    }

    @Nested
    @DisplayName("listAll")
    class ListAllTest {

        @Test
        @DisplayName("Deve converter todos os estoques em responses")
        void listAll_withStocks_returnsAllAsResponses() {
            UUID firstId = UUID.randomUUID();
            UUID secondId = UUID.randomUUID();
            Stock first = new Stock(new StockRequest(firstId, 10));
            Stock second = new Stock(new StockRequest(secondId, 5));
            second.reserve(2);

            when(stockRepository.findAll()).thenReturn(List.of(first, second));

            List<StockResponse> responses = stockService.listAll();

            assertThat(responses).hasSize(2);
            assertThat(responses.get(0).productId()).isEqualTo(firstId);
            assertThat(responses.get(0).available()).isEqualTo(10);
            assertThat(responses.get(0).reserved()).isZero();
            assertThat(responses.get(1).productId()).isEqualTo(secondId);
            assertThat(responses.get(1).available()).isEqualTo(3);
            assertThat(responses.get(1).reserved()).isEqualTo(2);
        }


    }
}
