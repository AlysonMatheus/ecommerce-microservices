package com.portfolio.inventory.entity;

import com.portfolio.inventory.dtos.request.StockRequest;
import com.portfolio.inventory.execption.InsufficientStockException;
import jakarta.persistence.*;
import lombok.*;

import javax.naming.InsufficientResourcesException;
import java.util.UUID;

@Getter

@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Entity
@Table(name = "stock")
public class Stock {

    @Id
    @Column(name = "product_id")
    private UUID productId;

    @Column(name = "available", nullable = false)
    private Integer available;

    @Column(name = "reserved", nullable = false)
    private Integer reserved = 0;

    public Stock(StockRequest dto) {

        if (dto == null) {
            throw new IllegalArgumentException(
                    "Os dados do estoque não podem ser nulos"
            );
        }

        if (dto.productId() == null) {
            throw new IllegalArgumentException(
                    "O identificador do produto não pode ser nulo"
            );
        }

        if (dto.available() == null || dto.available() < 0) {
            throw new IllegalArgumentException(
                    "A quantidade inicial deve ser maior ou igual a zero"
            );
        }

        this.productId = dto.productId();
        this.available = dto.available();
        this.reserved = 0;
    }
    public void reserve(int quantity) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("A quantidade a reservar deve ser maior que zero");
        }
        if (this.available < quantity) {
            throw new InsufficientStockException(productId, quantity, available);
        }
        this.available -= quantity;
        this.reserved += quantity;
    }

    public void release(int quantity) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("A quantidade a liberar deve ser maior que zero");
        }
        if (this.reserved < quantity) {
            throw new IllegalStateException(
                    "Não é possível liberar " + quantity + " unidades do produto " + productId
                            + ": apenas " + reserved + " estão reservadas");
        }
        this.reserved -= quantity;
        this.available += quantity;
    }

}
