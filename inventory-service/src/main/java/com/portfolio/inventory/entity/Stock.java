package com.portfolio.inventory.entity;

import com.portfolio.inventory.dtos.request.StockRequest;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import javax.naming.InsufficientResourcesException;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
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
        this.productId = dto.productId();
        this.available = dto.available();
        reserved = 0;

    }
    public void reserve(int quantity){
        if (quantity <= 0){
            throw new IllegalArgumentException("");
        }
        if (this.available < quantity){
            throw new RuntimeException("");
            }
            this.available -= quantity;
            this.reserved += quantity;
        }

    public void release(int quantity){
    if (quantity <= 0 || this.reserved < quantity){
        throw  new RuntimeException("");

    }
        this.reserved -= quantity;
        this.available += quantity;
    }

}
