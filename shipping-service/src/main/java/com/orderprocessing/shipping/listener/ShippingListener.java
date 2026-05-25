package com.orderprocessing.shipping.listener;

import com.orderprocessing.shared.events.Events;
import com.orderprocessing.shared.events.Topics;
import com.orderprocessing.shipping.service.ShippingService;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ShippingListener {

    private final ShippingService shippingService;

    @KafkaListener(topics = Topics.SHIPPING_DISPATCH, groupId = "shipping-service",
            containerFactory = "kafkaListenerContainerFactory")
    public void onDispatch(@Payload Events.OrderCreated e, Acknowledgment ack) {
        shippingService.dispatch(e.getOrderId());
        ack.acknowledge();
    }
}
