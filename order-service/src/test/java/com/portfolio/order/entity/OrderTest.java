
package com.portfolio.order.entity;

import com.portfolio.order.dtos.request.CreateOrderRequest;
import com.portfolio.order.dtos.request.OrderItemRequest;
import com.portfolio.order.enums.OrderStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OrderTest {

    private Order criarPedidoComItens() {
        List<OrderItemRequest> itens = List.of(
                new OrderItemRequest(
                        UUID.randomUUID(),
                        2,
                        new BigDecimal("15.00")
                ),
                new OrderItemRequest(
                        UUID.randomUUID(),
                        1,
                        new BigDecimal("20.00")
                )
        );

        CreateOrderRequest request = new CreateOrderRequest(
                UUID.randomUUID(),
                itens
        );

        return new Order(request);
    }

    @Nested
    @DisplayName("1. Criação do pedido")
    class ConstructorTest {

        @Test
        @DisplayName("Deve inicializar pedido com dois itens")
        void constructor_items_size() {
            Order order = criarPedidoComItens();

            assertThat(order.getItems()).hasSize(2);
            assertThat(order.getTotal()).isEqualByComparingTo("50.00");
        }

        @Test
        @DisplayName("Deve manter o total com escala de duas casas decimais")
        void constructor_total_shouldHaveScaleTwo() {
            OrderItemRequest item = new OrderItemRequest(
                    UUID.randomUUID(),
                    1,
                    new BigDecimal("10")
            );

            CreateOrderRequest request = new CreateOrderRequest(
                    UUID.randomUUID(),
                    List.of(item)
            );

            Order order = new Order(request);

            assertThat(order.getTotal()).isEqualByComparingTo("10.00");
            assertThat(order.getTotal().scale()).isEqualTo(2);
        }

        @Test
        @DisplayName("Deve arredondar o total usando HALF_UP")
        void constructor_total_shouldRoundHalfUp() {
            OrderItemRequest item = new OrderItemRequest(
                    UUID.randomUUID(),
                    1,
                    new BigDecimal("0.005")
            );

            CreateOrderRequest request = new CreateOrderRequest(
                    UUID.randomUUID(),
                    List.of(item)
            );

            Order order = new Order(request);

            assertThat(order.getTotal()).isEqualByComparingTo("0.01");
        }

        @Test
        @DisplayName("Deve criar pedido com status PENDING e customerId correto")
        void constructor_validRequest_initializesOrder() {
            UUID customerId = UUID.randomUUID();

            List<OrderItemRequest> items = List.of(
                    new OrderItemRequest(
                            UUID.randomUUID(),
                            2,
                            new BigDecimal("15.00")
                    )
            );

            CreateOrderRequest request = new CreateOrderRequest(
                    customerId,
                    items
            );

            Order order = new Order(request);

            assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING);
            assertThat(order.getCustomerId()).isEqualTo(customerId);
        }
    }

    @Nested
    @DisplayName("2. Adição de itens")
    class AddItemTest {

        @Test
        @DisplayName("Deve adicionar novos itens à lista")
        void add_items() {
            Order order = criarPedidoComItens();

            OrderItemRequest dto = new OrderItemRequest(
                    UUID.randomUUID(),
                    2,
                    new BigDecimal("10.99")
            );

            order.addItem(new OrderItem(dto));

            assertThat(order.getItems()).hasSize(3);
            assertThat(order.getTotal()).isEqualByComparingTo("71.98");
            assertThat(order.getItems().get(2).getOrder()).isSameAs(order);
        }
    }

    @Nested
    @DisplayName("3. Confirmação")
    class ConfirmTest {

        @Test
        @DisplayName("Deve confirmar um pedido pendente")
        void confirm_pendingOrder_changesStatusToConfirmed() {
            Order order = criarPedidoComItens();

            order.confirm();

            assertThat(order.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
        }

        @Test
        @DisplayName("Não deve permitir confirmar um pedido já confirmado")
        void confirm_alreadyConfirmed_throwsException() {
            Order order = criarPedidoComItens();

            order.confirm();

            assertThatThrownBy(() -> order.confirm())
                    .isInstanceOf(IllegalStateException.class);

            assertThat(order.getStatus())
                    .isEqualTo(OrderStatus.CONFIRMED);
        }

        @Test
        @DisplayName("Não deve permitir confirmar um pedido cancelado")
        void confirm_cancelledOrder_throwsException() {
            Order order = criarPedidoComItens();

            order.cancel();

            assertThatThrownBy(() -> order.confirm())
                    .isInstanceOf(IllegalStateException.class);

            assertThat(order.getStatus())
                    .isEqualTo(OrderStatus.CANCELLED);
        }
    }

    @Nested
    @DisplayName("4. Cancelamento")
    class CancelTest {

        @Test
        @DisplayName("Deve cancelar um pedido pendente")
        void cancel_pendingOrder_changesStatusToCancelled() {
            Order order = criarPedidoComItens();

            order.cancel();

            assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        }

        @Test
        @DisplayName("Não deve permitir cancelar um pedido já cancelado")
        void cancel_alreadyCancelled_throwsException() {
            Order order = criarPedidoComItens();

            order.cancel();

            assertThatThrownBy(() -> order.cancel())
                    .isInstanceOf(IllegalStateException.class);

            assertThat(order.getStatus())
                    .isEqualTo(OrderStatus.CANCELLED);
        }

        @Test
        @DisplayName("Não deve permitir cancelar um pedido confirmado")
        void cancel_confirmedOrder_throwsException() {
            Order order = criarPedidoComItens();

            order.confirm();

            assertThatThrownBy(() -> order.cancel())
                    .isInstanceOf(IllegalStateException.class);

            assertThat(order.getStatus())
                    .isEqualTo(OrderStatus.CONFIRMED);
        }
    }

    @Nested
    @DisplayName("5. Proteção da coleção")
    class GetItemsTest {

        @Test
        @DisplayName("Não deve permitir modificar diretamente a lista de itens")
        void getItems_shouldReturnUnmodifiableList() {
            Order order = criarPedidoComItens();

            OrderItemRequest dto = new OrderItemRequest(
                    UUID.randomUUID(),
                    2,
                    new BigDecimal("10.99")
            );

            assertThatThrownBy(
                    () -> order.getItems().add(new OrderItem(dto))
            ).isInstanceOf(UnsupportedOperationException.class);

            assertThat(order.getItems()).hasSize(2);
        }
    }
}
