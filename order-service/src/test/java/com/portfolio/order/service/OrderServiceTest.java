package com.portfolio.order.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.portfolio.order.dtos.request.CreateOrderRequest;
import com.portfolio.order.dtos.request.OrderItemRequest;
import com.portfolio.order.dtos.response.CreateOrderResponse;
import com.portfolio.order.dtos.response.OrderItemResponse;
import com.portfolio.order.entity.Order;
import com.portfolio.order.enums.OrderStatus;
import com.portfolio.order.exception.OrderNotFoundException;
import com.portfolio.order.messaging.config.RabbitConfig;
import com.portfolio.order.messaging.outbox.OutBoxEvent;
import com.portfolio.order.repository.OrderRepository;
import com.portfolio.order.repository.OutBoxEventRepository;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.mockito.InjectMocks;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;


@ExtendWith(MockitoExtension.class)
@DisplayName("Testes do OrderService")
class OrderServiceTest {

    @Mock
    private OrderRepository orderRepository;
    @Mock
    private OutBoxEventRepository outboxEventRepository;
    @Spy
    private ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @InjectMocks
    private OrderService orderService;


    @Nested
    @DisplayName("create")
    class CreateTest {
        @Test
        @DisplayName("Deve salvar o pedido e gravar order.created no outbox")
        void create_validRequest_savesOrderAndRecordsOrderCreatedInOutbox() {
            UUID orderId = UUID.randomUUID();
            UUID productId = UUID.randomUUID();
            CreateOrderRequest request = new CreateOrderRequest(
                    UUID.randomUUID(),
                    List.of(new OrderItemRequest(productId, 2, new BigDecimal("15.00"))));

            when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> {
                Order order = invocation.getArgument(0);
                ReflectionTestUtils.setField(order, "id", orderId);
                return order;
            });

            orderService.create(request);

            ArgumentCaptor<OutBoxEvent> captor = ArgumentCaptor.forClass(OutBoxEvent.class);
            verify(outboxEventRepository).save(captor.capture());

            OutBoxEvent saved = captor.getValue();
            assertThat(saved.getRoutingKey()).isEqualTo(RabbitConfig.ORDER_CREATED_KEY);
            assertThat(saved.getAggregateId()).isEqualTo(orderId);
            assertThat(saved.getPayload()).contains(orderId.toString()).contains(productId.toString());
        }
    }

    @Nested
    @DisplayName("findById")
    class FindByIdTest {
        @Test
        @DisplayName("Deve devolver o pedido encontrado")
        void findById_existingOrder_returnsResponse() {
            UUID orderId = UUID.randomUUID();
            UUID customerId = UUID.randomUUID();
            CreateOrderRequest request = new CreateOrderRequest(
                    customerId,
                    List.of(new OrderItemRequest(UUID.randomUUID(), 2, new BigDecimal("15.00"))));
            Order order = new Order(request);
            ReflectionTestUtils.setField(order, "id", orderId);

            when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));

            CreateOrderResponse response = new CreateOrderResponse(order);


            assertThat(response.id()).isEqualTo(orderId);
            assertThat(response.customerId()).isEqualTo(customerId);
            assertThat(response.status()).isEqualTo(OrderStatus.PENDING);
            assertThat(response.total()).isEqualByComparingTo("30.00");
        }

        @Test
        @DisplayName("Deve lançar OrderNotFoundException quando o pedido não existe")
        void findById_unknownOrder_throwsOrderNotFound() {
            UUID orderId = UUID.randomUUID();

            when(orderRepository.findById(orderId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> orderService.findById(orderId)).isInstanceOf(OrderNotFoundException.class).hasMessageContaining(orderId.toString());




        }
    }

    @Nested
    @DisplayName("listAll")
    class ListAllTest {

        @Test
        @DisplayName("Deve converter todos os pedidos em responses")
        void listAll_withOrders_returnsAllAsResponses() {
            UUID firstId = UUID.randomUUID();
            UUID secondId = UUID.randomUUID();
            UUID firstCustomerId = UUID.randomUUID();
            UUID secondCustomerId = UUID.randomUUID();

            Order first = new Order(new CreateOrderRequest(
                    firstCustomerId,
                    List.of(new OrderItemRequest(UUID.randomUUID(), 2, new BigDecimal("15.00")))));
            Order second = new Order(new CreateOrderRequest(
                    secondCustomerId,
                    List.of(new OrderItemRequest(UUID.randomUUID(), 1, new BigDecimal("20.00")))));
            second.confirm();


            ReflectionTestUtils.setField(first, "id", firstId);
            ReflectionTestUtils.setField(second, "id", secondId);

            when(orderRepository.findAll()).thenReturn(List.of(first, second));

            List<CreateOrderResponse> responses = orderService.listAll();

            assertThat(responses).hasSize(2);

            assertThat(responses.get(0).id()).isEqualTo(firstId);
            assertThat(responses.get(0).customerId()).isEqualTo(firstCustomerId);
            assertThat(responses.get(0).status()).isEqualTo(OrderStatus.PENDING);
            assertThat(responses.get(0).total()).isEqualByComparingTo("30.00");

            assertThat(responses.get(1).id()).isEqualTo(secondId);
            assertThat(responses.get(1).customerId()).isEqualTo(secondCustomerId);
            assertThat(responses.get(1).status()).isEqualTo(OrderStatus.CONFIRMED);
            assertThat(responses.get(1).total()).isEqualByComparingTo("20.00");
        }
    }

    @Nested
    @DisplayName("confirm")
    class ConfirmTest {

        @Test
        @DisplayName("Deve confirmar um pedido pendente")
        void confirm_pendingOrder_changesStatusToConfirmed() {
            UUID orderId = UUID.randomUUID();
            Order order = new Order(new CreateOrderRequest(
                    UUID.randomUUID(),
                    List.of(new OrderItemRequest(UUID.randomUUID(), 2, new BigDecimal("15.00")))));

            when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));

            orderService.confirm(orderId);

            assertThat(order.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
        }

        @Test
        @DisplayName("Deve ignorar a confirmação de um pedido já resolvido, sem lançar exceção")
        void confirm_alreadyResolvedOrder_ignoresWithoutException() {
            UUID orderId = UUID.randomUUID();
            Order order = new Order(new CreateOrderRequest(
                    UUID.randomUUID(),
                    List.of(new OrderItemRequest(UUID.randomUUID(), 2, new BigDecimal("15.00")))));
            order.cancel();
            when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));

            assertThatCode(()-> orderService.confirm(orderId)).doesNotThrowAnyException();
            assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);

        }

        @Test
        @DisplayName("Deve lançar OrderNotFoundException quando o pedido não existe")
        void confirm_unknownOrder_throwsOrderNotFound() {
            UUID orderId = UUID.randomUUID();
            when(orderRepository.findById(orderId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> orderService.findById(orderId)).isInstanceOf(OrderNotFoundException.class);


        }
    }

    @Nested
    @DisplayName("cancel")
    class CancelTest {

        @Test
        @DisplayName("Deve cancelar um pedido pendente")
        void cancel_pendingOrder_changesStatusToCancelled() {
            UUID orderId = UUID.randomUUID();
            Order order = new Order(new CreateOrderRequest(
                    UUID.randomUUID(),
                    List.of(new OrderItemRequest(UUID.randomUUID(), 2, new BigDecimal("15.00")))));

            when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));

            orderService.cancel(orderId);

            assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        }

        @Test
        @DisplayName("Deve ignorar o cancelamento de um pedido já resolvido, sem lançar exceção")
        void cancel_alreadyResolvedOrder_ignoresWithoutException() {
        }

        @Test
        @DisplayName("Deve lançar OrderNotFoundException quando o pedido não existe")
        void cancel_unknownOrder_throwsOrderNotFound() {
        }
    }
}