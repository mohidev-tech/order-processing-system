package com.orderprocessing.inventory.listener;

import com.orderprocessing.inventory.service.InventoryService;
import com.orderprocessing.shared.events.Events;
import com.orderprocessing.shared.events.Topics;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class InventoryListener {

    private final InventoryService inventoryService;

    @KafkaListener(topics = Topics.INVENTORY_RESERVE, groupId = "inventory-service",
            containerFactory = "kafkaListenerContainerFactory")
    public void onReserve(@Payload Events.OrderCreated e, Acknowledgment ack) {
        inventoryService.reserve(e.getOrderId());
        ack.acknowledge();
    }

    @KafkaListener(topics = Topics.INVENTORY_RELEASE, groupId = "inventory-service",
            containerFactory = "kafkaListenerContainerFactory")
    public void onRelease(@Payload Events.InventoryRelease e, Acknowledgment ack) {
        inventoryService.release(e.getOrderId(), e.getReservationId());
        ack.acknowledge();
    }
}
