
package com.portfolio.inventory.entity;

import com.portfolio.inventory.dtos.request.StockRequest;
import com.portfolio.inventory.execption.InsufficientStockException;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Testes da entidade Stock")
class StockTest {

    private Stock criarStockComEstoqueDe(int quantidadeInicial) {
        return new Stock(
                new StockRequest(UUID.randomUUID(), quantidadeInicial)
        );
    }

    @Nested
    @DisplayName("1. Inicialização do estoque")
    class StockConstructorTest {

        @Test
        @DisplayName("Deve inicializar o estoque com a quantidade informada")
        void constructor_validQuantity_initializesStock() {
            Stock stock = criarStockComEstoqueDe(10);

            assertThat(stock.getAvailable()).isEqualTo(10);
            assertThat(stock.getReserved()).isZero();
        }

        @Test
        @DisplayName("Deve permitir inicializar estoque sem unidades")
        void constructor_zeroQuantity_initializesEmptyStock() {
            Stock stock = criarStockComEstoqueDe(0);

            assertThat(stock.getAvailable()).isZero();
            assertThat(stock.getReserved()).isZero();
        }

        @Test
        @DisplayName("Não deve permitir estoque inicial negativo")
        void constructor_negativeQuantity_throwsException() {

            assertThatThrownBy(() -> criarStockComEstoqueDe(-10))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("Não deve permitir productId nulo")
        void constructor_nullProductId_throwsException() {
            StockRequest request = new StockRequest(null, 10);
            assertThatThrownBy(() -> new Stock(request)).isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("Não deve permitir quantidade inicial nula")
        void constructor_nullQuantity_throwsException() {
            StockRequest request = new StockRequest(UUID.randomUUID(), null);

            assertThatThrownBy(() -> new Stock(request))
                    .isInstanceOf(IllegalArgumentException.class);
        }
        @Test
        @DisplayName("Não deve permitir StockRequest nulo")
        void constructor_nullRequest_throwsException() {

            StockRequest request = null;

            assertThatThrownBy(() -> new Stock(request))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

        @Nested
        @DisplayName("2. Reserva de estoque")
        class StockReserveTest {

            @Test
            @DisplayName("Deve reservar unidades disponíveis com sucesso")
            void reserve_success() {
                Stock stock = criarStockComEstoqueDe(10);

                stock.reserve(9);

                assertThat(stock.getAvailable()).isEqualTo(1);
                assertThat(stock.getReserved()).isEqualTo(9);
            }

            @Test
            @DisplayName("Não deve reservar mais unidades do que estão disponíveis")
            void reserve_moreThanAvailable_throwsAndKeepsState() {
                Stock stock = criarStockComEstoqueDe(10);

                assertThatThrownBy(() -> stock.reserve(12))
                        .isInstanceOf(InsufficientStockException.class);

                assertThat(stock.getAvailable()).isEqualTo(10);
                assertThat(stock.getReserved()).isZero();
            }

            @Test
            @DisplayName("Deve permitir reservar a quantidade exata disponível")
            void reserve_exactQuantity_zerosAvailable() {
                Stock stock = criarStockComEstoqueDe(10);

                stock.reserve(10);

                assertThat(stock.getAvailable()).isZero();
                assertThat(stock.getReserved()).isEqualTo(10);
            }

            @Test
            @DisplayName("Deve acumular duas reservas consecutivas")
            void reserve_multipleReservations_accumulatesReserved() {
                Stock stock = criarStockComEstoqueDe(10);

                stock.reserve(3);
                stock.reserve(2);

                assertThat(stock.getAvailable()).isEqualTo(5);
                assertThat(stock.getReserved()).isEqualTo(5);
            }

            @Test
            @DisplayName("Deve manter a primeira reserva quando a segunda falhar")
            void reserve_secondReservationFails_keepsPreviousState() {
                Stock stock = criarStockComEstoqueDe(10);

                stock.reserve(6);

                assertThatThrownBy(() -> stock.reserve(5))
                        .isInstanceOf(InsufficientStockException.class);

                assertThat(stock.getAvailable()).isEqualTo(4);
                assertThat(stock.getReserved()).isEqualTo(6);
            }

            @ParameterizedTest
            @ValueSource(ints = {0, -1})
            @DisplayName("Deve rejeitar reservas com quantidade zero ou negativa")
            void reserve_invalidQuantity_throwsIllegalArgumentException(
                    int quantidadeInvalida) {

                Stock stock = criarStockComEstoqueDe(10);

                assertThatThrownBy(() -> stock.reserve(quantidadeInvalida))
                        .isInstanceOf(IllegalArgumentException.class);

                assertThat(stock.getAvailable()).isEqualTo(10);
                assertThat(stock.getReserved()).isZero();
            }
        }

        @Nested
        @DisplayName("3. Liberação de estoque")
        class StockReleaseTest {

            @Test
            @DisplayName("Deve liberar parcialmente uma reserva")
            void release_partialQuantity_updatesStock() {
                Stock stock = criarStockComEstoqueDe(10);

                stock.reserve(6);
                stock.release(2);

                assertThat(stock.getAvailable()).isEqualTo(6);
                assertThat(stock.getReserved()).isEqualTo(4);
            }

            @Test
            @DisplayName("Não deve liberar mais unidades do que estão reservadas")
            void release_moreThanReserved_throwsAndKeepsState() {
                Stock stock = criarStockComEstoqueDe(10);

                stock.reserve(4);

                assertThatThrownBy(() -> stock.release(5))
                        .isInstanceOf(IllegalStateException.class);

                assertThat(stock.getAvailable()).isEqualTo(6);
                assertThat(stock.getReserved()).isEqualTo(4);
            }

            @ParameterizedTest
            @ValueSource(ints = {0, -1})
            @DisplayName("Deve rejeitar liberações com quantidade zero ou negativa")
            void release_invalidQuantity_throwsIllegalArgumentException(
                    int quantidadeInvalida) {

                Stock stock = criarStockComEstoqueDe(10);
                stock.reserve(5);

                assertThatThrownBy(() -> stock.release(quantidadeInvalida))
                        .isInstanceOf(IllegalArgumentException.class);

                assertThat(stock.getAvailable()).isEqualTo(5);
                assertThat(stock.getReserved()).isEqualTo(5);
            }

            @Test
            @DisplayName("Deve liberar todas as unidades reservadas")
            void release_allReserved_restoresAvailable() {
                Stock stock = criarStockComEstoqueDe(10);

                stock.reserve(10);
                stock.release(10);

                assertThat(stock.getAvailable()).isEqualTo(10);
                assertThat(stock.getReserved()).isZero();
            }

            @Test
            @DisplayName("Deve restaurar o estado inicial após reservar e liberar")
            void reserveAndRelease_sameQuantity_restoresInitialState() {
                Stock stock = criarStockComEstoqueDe(15);

                int initialAvailable = stock.getAvailable();
                int initialReserved = stock.getReserved();

                stock.reserve(7);
                stock.release(7);

                assertThat(stock.getAvailable()).isEqualTo(initialAvailable);
                assertThat(stock.getReserved()).isEqualTo(initialReserved);
            }
        }

}

