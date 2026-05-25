package com.orderprocessing.order.listener;

import com.orderprocessing.order.service.SagaOrchestrator;
import com.orderprocessing.shared.events.Events;
import com.orderprocessing.shared.events.Topics;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@RequiredArgsConstructor
public class SagaEventListener {

    private final SagaOrchestrator orchestrator;

    @KafkaListener(topics = Topics.INVENTORY_RESERVED, groupId = "order-service",
            containerFactory = "kafkaListenerContainerFactory")
    public void onInventoryReserved(@Payload Events.InventoryReserved e, Acknowledgment ack) {
        try {
            orchestrator.onInventoryReserved(e);
            ack.acknowledge();
        } catch (Exception ex) { log.error("inventory.reserved failed: {}", e, ex); }
    }

    @KafkaListener(topics = Topics.INVENTORY_FAILED, groupId = "order-service",
            containerFactory = "kafkaListenerContainerFactory")
    public void onInventoryFailed(@Payload Events.InventoryFailed e, Acknowledgment ack) {
        try {
            orchestrator.onInventoryFailed(e);
            ack.acknowledge();
        } catch (Exception ex) { log.error("inventory.failed handler failed: {}", e, ex); }
    }

    @KafkaListener(topics = Topics.PAYMENT_PROCESSED, groupId = "order-service",
            containerFactory = "kafkaListenerContainerFactory")
    public void onPaymentProcessed(@Payload Events.PaymentProcessed e, Acknowledgment ack) {
        try {
            orchestrator.onPaymentProcessed(e);
            ack.acknowledge();
        } catch (Exception ex) { log.error("payment.processed failed: {}", e, ex); }
    }

    @KafkaListener(topics = Topics.PAYMENT_FAILED, groupId = "order-service",
            containerFactory = "kafkaListenerContainerFactory")
    public void onPaymentFailed(@Payload Events.PaymentFailed e, Acknowledgment ack) {
        try {
            orchestrator.onPaymentFailed(e);
            ack.acknowledge();
        } catch (Exception ex) { log.error("payment.failed handler failed: {}", e, ex); }
    }

    @KafkaListener(topics = Topics.SHIPPING_COMPLETED, groupId = "order-service",
            containerFactory = "kafkaListenerContainerFactory")
    public void onShippingCompleted(@Payload Events.ShippingCompleted e, Acknowledgment ack) {
        try {
            orchestrator.onShippingCompleted(e);
            ack.acknowledge();
        } catch (Exception ex) { log.error("shipping.completed failed: {}", e, ex); }
    }

    @KafkaListener(topics = Topics.SHIPPING_FAILED, groupId = "order-service",
            containerFactory = "kafkaListenerContainerFactory")
    public void onShippingFailed(@Payload Events.ShippingFailed e, Acknowledgment ack) {
        try {
            orchestrator.onShippingFailed(e);
            ack.acknowledge();
        } catch (Exception ex) { log.error("shipping.failed handler failed: {}", e, ex); }
    }
}
