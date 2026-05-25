package com.orderprocessing.order.controller;

import com.orderprocessing.order.domain.Order;
import com.orderprocessing.order.domain.OrderRepository;
import com.orderprocessing.order.service.SagaOrchestrator;
import com.orderprocessing.shared.events.Events;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderController {

    private final SagaOrchestrator orchestrator;
    private final OrderRepository orderRepository;

    @PostMapping
    public ResponseEntity<Order> create(@RequestBody Events.OrderCreated request) {
        if (request.getOrderId() == null || request.getOrderId().isBlank()) {
            request.setOrderId(UUID.randomUUID().toString());
        }
        Order o = orchestrator.startSaga(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(o);
    }

    @GetMapping("/{orderId}")
    public ResponseEntity<Order> get(@PathVariable String orderId) {
        return orderRepository.findById(orderId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping
    public ResponseEntity<?> all() {
        return ResponseEntity.ok(orderRepository.findAll());
    }
}
