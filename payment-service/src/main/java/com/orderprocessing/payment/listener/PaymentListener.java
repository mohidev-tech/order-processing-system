package com.orderprocessing.payment.listener;

import com.orderprocessing.payment.service.PaymentService;
import com.orderprocessing.shared.events.Events;
import com.orderprocessing.shared.events.Topics;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PaymentListener {

    private final PaymentService paymentService;

    @KafkaListener(topics = Topics.PAYMENT_PROCESS, groupId = "payment-service",
            containerFactory = "kafkaListenerContainerFactory")
    public void onProcess(@Payload Events.OrderCreated e, Acknowledgment ack) {
        paymentService.process(e.getOrderId());
        ack.acknowledge();
    }

    @KafkaListener(topics = Topics.PAYMENT_REFUND, groupId = "payment-service",
            containerFactory = "kafkaListenerContainerFactory")
    public void onRefund(@Payload Events.PaymentRefund e, Acknowledgment ack) {
        paymentService.refund(e.getOrderId(), e.getPaymentId());
        ack.acknowledge();
    }
}
