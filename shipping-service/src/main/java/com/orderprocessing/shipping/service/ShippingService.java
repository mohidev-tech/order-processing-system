package com.orderprocessing.shipping.service;

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
public class ShippingService {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Value("${shipping.failure-rate:0}")
    private int failureRatePercent;

    public void dispatch(String orderId) {
        if (ThreadLocalRandom.current().nextInt(100) < failureRatePercent) {
            log.warn("[SHIP] {} dispatch FAILED (injected)", orderId);
            kafkaTemplate.send(Topics.SHIPPING_FAILED, orderId,
                    Events.ShippingFailed.builder()
                            .orderId(orderId)
                            .reason("warehouse unreachable (injected failure)")
                            .build());
            return;
        }
        String trackingId = "TRK-" + UUID.randomUUID();
        log.info("[SHIP] {} dispatched: {}", orderId, trackingId);
        kafkaTemplate.send(Topics.SHIPPING_COMPLETED, orderId,
                Events.ShippingCompleted.builder()
                        .orderId(orderId)
                        .trackingId(trackingId)
                        .build());
    }
}
