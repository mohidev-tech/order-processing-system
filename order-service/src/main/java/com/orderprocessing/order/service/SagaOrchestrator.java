package com.orderprocessing.order.service;

import com.orderprocessing.order.domain.Order;
import com.orderprocessing.order.domain.Order.SagaState;
import com.orderprocessing.order.domain.OrderRepository;
import com.orderprocessing.shared.events.Events;
import com.orderprocessing.shared.events.Topics;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * The saga state machine — the orchestrator pattern.
 *
 * Each handle*() method:
 *   1. Loads the order (the saga state machine).
 *   2. Asserts the transition is legal for the current state (idempotency).
 *   3. Persists the new state.
 *   4. Emits the next command/event.
 *
 * Compensation events flow backwards on failure:
 *   payment.failed   -> inventory.release
 *   shipping.failed  -> payment.refund + inventory.release
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class SagaOrchestrator {

    private final OrderRepository orderRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Transactional
    public Order startSaga(Events.OrderCreated event) {
        Order order = Order.builder()
                .orderId(event.getOrderId())
                .userId(event.getUserId())
                .totalAmount(event.getTotalAmount())
                .state(SagaState.CREATED)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        orderRepository.save(order);

        log.info("[SAGA] {} -> CREATED, emitting inventory.reserve", order.getOrderId());
        kafkaTemplate.send(Topics.INVENTORY_RESERVE, order.getOrderId(), event);
        return order;
    }

    @Transactional
    public void onInventoryReserved(Events.InventoryReserved e) {
        Order o = load(e.getOrderId());
        if (o.getState() != SagaState.CREATED) {
            log.warn("[SAGA] {} got inventory.reserved in unexpected state {}", o.getOrderId(), o.getState());
            return;
        }
        o.setReservationId(e.getReservationId());
        o.setState(SagaState.INVENTORY_RESERVED);
        o.setUpdatedAt(LocalDateTime.now());
        orderRepository.save(o);

        log.info("[SAGA] {} -> INVENTORY_RESERVED, emitting payment.process", o.getOrderId());
        kafkaTemplate.send(Topics.PAYMENT_PROCESS, o.getOrderId(),
                Events.OrderCreated.builder()
                        .orderId(o.getOrderId())
                        .userId(o.getUserId())
                        .totalAmount(o.getTotalAmount())
                        .build());
    }

    @Transactional
    public void onInventoryFailed(Events.InventoryFailed e) {
        Order o = load(e.getOrderId());
        log.warn("[SAGA] {} inventory FAILED: {} — cancelling", o.getOrderId(), e.getReason());
        cancel(o, e.getReason());
    }

    @Transactional
    public void onPaymentProcessed(Events.PaymentProcessed e) {
        Order o = load(e.getOrderId());
        if (o.getState() != SagaState.INVENTORY_RESERVED) {
            log.warn("[SAGA] {} got payment.processed in unexpected state {}", o.getOrderId(), o.getState());
            return;
        }
        o.setPaymentId(e.getPaymentId());
        o.setState(SagaState.PAYMENT_PROCESSED);
        o.setUpdatedAt(LocalDateTime.now());
        orderRepository.save(o);

        log.info("[SAGA] {} -> PAYMENT_PROCESSED, emitting shipping.dispatch", o.getOrderId());
        kafkaTemplate.send(Topics.SHIPPING_DISPATCH, o.getOrderId(),
                Events.OrderCreated.builder()
                        .orderId(o.getOrderId())
                        .userId(o.getUserId())
                        .totalAmount(o.getTotalAmount())
                        .build());
    }

    @Transactional
    public void onPaymentFailed(Events.PaymentFailed e) {
        Order o = load(e.getOrderId());
        log.warn("[SAGA] {} payment FAILED: {} — compensating inventory", o.getOrderId(), e.getReason());
        o.setState(SagaState.COMPENSATING_INVENTORY);
        o.setUpdatedAt(LocalDateTime.now());
        o.setFailureReason(e.getReason());
        orderRepository.save(o);
        kafkaTemplate.send(Topics.INVENTORY_RELEASE, o.getOrderId(),
                Events.InventoryRelease.builder()
                        .orderId(o.getOrderId())
                        .reservationId(o.getReservationId())
                        .build());
        // Inventory service emits no event for the release (best-effort);
        // we mark cancelled here. Production: wait for inventory.released ack.
        cancel(o, e.getReason());
    }

    @Transactional
    public void onShippingCompleted(Events.ShippingCompleted e) {
        Order o = load(e.getOrderId());
        if (o.getState() != SagaState.PAYMENT_PROCESSED) {
            log.warn("[SAGA] {} got shipping.completed in unexpected state {}", o.getOrderId(), o.getState());
            return;
        }
        o.setTrackingId(e.getTrackingId());
        o.setState(SagaState.COMPLETED);
        o.setUpdatedAt(LocalDateTime.now());
        orderRepository.save(o);

        log.info("[SAGA] {} -> COMPLETED", o.getOrderId());
        kafkaTemplate.send(Topics.ORDER_COMPLETED, o.getOrderId(),
                Events.OrderCompleted.builder()
                        .orderId(o.getOrderId())
                        .trackingId(o.getTrackingId())
                        .build());
    }

    @Transactional
    public void onShippingFailed(Events.ShippingFailed e) {
        Order o = load(e.getOrderId());
        log.warn("[SAGA] {} shipping FAILED: {} — compensating payment + inventory", o.getOrderId(), e.getReason());
        o.setState(SagaState.COMPENSATING_PAYMENT);
        o.setFailureReason(e.getReason());
        o.setUpdatedAt(LocalDateTime.now());
        orderRepository.save(o);

        kafkaTemplate.send(Topics.PAYMENT_REFUND, o.getOrderId(),
                Events.PaymentRefund.builder()
                        .orderId(o.getOrderId())
                        .paymentId(o.getPaymentId())
                        .build());
        kafkaTemplate.send(Topics.INVENTORY_RELEASE, o.getOrderId(),
                Events.InventoryRelease.builder()
                        .orderId(o.getOrderId())
                        .reservationId(o.getReservationId())
                        .build());

        cancel(o, e.getReason());
    }

    private void cancel(Order o, String reason) {
        o.setState(SagaState.CANCELLED);
        o.setFailureReason(reason);
        o.setUpdatedAt(LocalDateTime.now());
        orderRepository.save(o);
        kafkaTemplate.send(Topics.ORDER_CANCELLED, o.getOrderId(),
                Events.OrderCancelled.builder().orderId(o.getOrderId()).reason(reason).build());
    }

    private Order load(String orderId) {
        return orderRepository.findById(orderId)
                .orElseThrow(() -> new IllegalStateException("saga state missing for orderId=" + orderId));
    }
}
