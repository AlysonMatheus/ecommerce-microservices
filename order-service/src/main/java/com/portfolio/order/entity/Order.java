package com.portfolio.order.entity;

import com.portfolio.order.dtos.request.CreateOrderRequest;
import com.portfolio.order.enums.OrderStatus;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "orders")
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id")
    private UUID id;

    @Column(name = "customer_id", nullable = false)
    private UUID customerId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private OrderStatus status = OrderStatus.PENDING;

    @Column(name = "total", nullable = false, precision = 12, scale = 2)
    private BigDecimal total = BigDecimal.ZERO;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<OrderItem> items = new ArrayList<>();

    public Order(CreateOrderRequest dto) {
        this.customerId = dto.customerId();
        dto.items().forEach(i -> addItem(new OrderItem(i)));
    }

    @PrePersist
    void onCreate() {
        this.createdAt = LocalDateTime.now();
    }

    public void addItem(OrderItem item) {
        item.setOrder(this);
        this.items.add(item);
        recalculateTotal();
    }

    public void confirm() {
        requirePending();
        this.status = OrderStatus.CONFIRMED;
    }

    public void cancel() {
        requirePending();
        this.status = OrderStatus.CANCELLED;
    }

    public List<OrderItem> getItems() {
        return Collections.unmodifiableList(items);
    }

    private void requirePending() {
        if (this.status != OrderStatus.PENDING) {
            throw new IllegalStateException(
                    "Pedido " + id + " já está " + status + " e não pode mudar de estado");
        }
    }

    private void recalculateTotal() {
        this.total = items.stream()
                .map(OrderItem::getSubtotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
    }
}