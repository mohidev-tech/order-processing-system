package com.orderprocessing.inventory.service;

import com.orderprocessing.shared.events.Events;
import com.orderprocessing.shared.events.Topics;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Toy inventory. Reservations live in a ConcurrentHashMap; in real life this
 * is a row-locked database or a dedicated inventory service with its own
 * stock-keeping unit tables. The interface and event shape are what matter
 * for the saga demo.
 *
 * Failure injection: a configurable percent of reservations randomly fail to
 * exercise the saga's failure-path compensation logic. Set to 0 for happy path.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class InventoryService {

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final Map<String, String> reservations = new ConcurrentHashMap<>(); // orderId -> reservationId

    @Value("${inventory.failure-rate:0}")
    private int failureRatePercent;

    public void reserve(String orderId) {
        if (ThreadLocalRandom.current().nextInt(100) < failureRatePercent) {
            log.warn("[INV] {} reservation FAILED (injected)", orderId);
            kafkaTemplate.send(Topics.INVENTORY_FAILED, orderId,
                    Events.InventoryFailed.builder()
                            .orderId(orderId)
                            .reason("inventory exhausted (injected failure)")
                            .build());
            return;
        }
        String reservationId = "RES-" + UUID.randomUUID();
        reservations.put(orderId, reservationId);
        log.info("[INV] {} reservation OK: {}", orderId, reservationId);
        kafkaTemplate.send(Topics.INVENTORY_RESERVED, orderId,
                Events.InventoryReserved.builder()
                        .orderId(orderId)
                        .reservationId(reservationId)
                        .build());
    }

    public void release(String orderId, String reservationId) {
        String removed = reservations.remove(orderId);
        log.info("[INV] {} reservation RELEASED (was {})", orderId, removed);
    }

    public Map<String, Object> stats() {
        Map<String, Object> s = new HashMap<>();
        s.put("activeReservations", reservations.size());
        s.put("failureRatePercent", failureRatePercent);
        return s;
    }
}
