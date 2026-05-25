package com.orderprocessing.payment.service;

import com.orderprocessing.shared.events.Events;
import com.orderprocessing.shared.events.Topics;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

@Service
@Slf4j
@RequiredArgsConstructor
public class PaymentService {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Value("${payment.failure-rate:0}")
    private int failureRatePercent;

    public void process(String orderId) {
        if (ThreadLocalRandom.current().nextInt(100) < failureRatePercent) {
            log.warn("[PAY] {} payment FAILED (injected)", orderId);
            kafkaTemplate.send(Topics.PAYMENT_FAILED, orderId,
                    Events.PaymentFailed.builder()
                            .orderId(orderId)
                            .reason("payment declined (injected failure)")
                            .build());
            return;
        }
        String paymentId = "PAY-" + UUID.randomUUID();
        log.info("[PAY] {} payment OK: {}", orderId, paymentId);
        kafkaTemplate.send(Topics.PAYMENT_PROCESSED, orderId,
                Events.PaymentProcessed.builder()
                        .orderId(orderId)
                        .paymentId(paymentId)
                        .build());
    }

    public void refund(String orderId, String paymentId) {
        log.info("[PAY] {} refund issued for {}", orderId, paymentId);
    }
}
