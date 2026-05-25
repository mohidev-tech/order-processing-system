package com.orderprocessing.shared.events;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;

/**
 * Saga events. Concrete POJOs (records would also work but Lombok @Data
 * makes them play nicer with the JSON deserializer's reflective access).
 *
 * Every event carries the orderId — that's the saga correlation key.
 */
public final class Events {

    private Events() {}

    @Data @NoArgsConstructor @AllArgsConstructor @Builder
    public static class OrderItem implements Serializable {
        private String productId;
        private int quantity;
        private BigDecimal price;
    }

    @Data @NoArgsConstructor @AllArgsConstructor @Builder
    public static class OrderCreated implements Serializable {
        private String orderId;
        private String userId;
        private BigDecimal totalAmount;
        private List<OrderItem> items;
    }

    @Data @NoArgsConstructor @AllArgsConstructor @Builder
    public static class InventoryReserved implements Serializable {
        private String orderId;
        private String reservationId;
    }

    @Data @NoArgsConstructor @AllArgsConstructor @Builder
    public static class InventoryFailed implements Serializable {
        private String orderId;
        private String reason;
    }

    @Data @NoArgsConstructor @AllArgsConstructor @Builder
    public static class InventoryRelease implements Serializable {
        private String orderId;
        private String reservationId;
    }

    @Data @NoArgsConstructor @AllArgsConstructor @Builder
    public static class PaymentProcessed implements Serializable {
        private String orderId;
        private String paymentId;
    }

    @Data @NoArgsConstructor @AllArgsConstructor @Builder
    public static class PaymentFailed implements Serializable {
        private String orderId;
        private String reason;
    }

    @Data @NoArgsConstructor @AllArgsConstructor @Builder
    public static class PaymentRefund implements Serializable {
        private String orderId;
        private String paymentId;
    }

    @Data @NoArgsConstructor @AllArgsConstructor @Builder
    public static class ShippingCompleted implements Serializable {
        private String orderId;
        private String trackingId;
    }

    @Data @NoArgsConstructor @AllArgsConstructor @Builder
    public static class ShippingFailed implements Serializable {
        private String orderId;
        private String reason;
    }

    @Data @NoArgsConstructor @AllArgsConstructor @Builder
    public static class OrderCompleted implements Serializable {
        private String orderId;
        private String trackingId;
    }

    @Data @NoArgsConstructor @AllArgsConstructor @Builder
    public static class OrderCancelled implements Serializable {
        private String orderId;
        private String reason;
    }
}
