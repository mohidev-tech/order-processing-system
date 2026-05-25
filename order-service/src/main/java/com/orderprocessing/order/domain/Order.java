package com.orderprocessing.order.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * The saga state machine. Persisted in Postgres so we recover from crashes
 * mid-saga and don't re-enter an already-completed state.
 */
@Entity
@Table(name = "orders")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Order {

    @Id
    private String orderId;

    @Column(nullable = false)
    private String userId;

    @Column(nullable = false)
    private BigDecimal totalAmount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SagaState state;

    private String reservationId;
    private String paymentId;
    private String trackingId;
    private String failureReason;

    @Column(nullable = false)
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @Version
    private Long version;

    public enum SagaState {
        CREATED,
        INVENTORY_RESERVED,
        PAYMENT_PROCESSED,
        SHIPPED,
        COMPLETED,
        // compensation states
        COMPENSATING_INVENTORY,
        COMPENSATING_PAYMENT,
        CANCELLED
    }
}
