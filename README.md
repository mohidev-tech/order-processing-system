# 📦 order-processing-system

> **Distributed transaction across 4 microservices via the SAGA orchestrator pattern. Postgres-backed state machine + Kafka choreography + compensation events on failure. Failure injection knobs to demonstrate the compensation path.**

[![ci](https://github.com/mohidev-tech/order-processing-system/actions/workflows/ci.yml/badge.svg)](https://github.com/mohidev-tech/order-processing-system/actions/workflows/ci.yml)
[![License: Apache 2.0](https://img.shields.io/badge/license-Apache--2.0-blue.svg)](LICENSE)
[![Java 21](https://img.shields.io/badge/java-21-orange.svg)](https://adoptium.net/)
[![Spring Boot 3.3](https://img.shields.io/badge/spring--boot-3.3-6DB33F?logo=spring)](https://spring.io/projects/spring-boot)
[![Kafka](https://img.shields.io/badge/kafka-7.5-231F20?logo=apachekafka)](https://kafka.apache.org/)

---

## What it does (real terminal output — happy path)

```
$ docker-compose up -d                # kafka + postgres
$ for svc in order inventory payment shipping; do
    (cd $svc-service && mvn -q spring-boot:run &)
  done

$ curl -s -X POST -H 'Content-Type: application/json' \
    http://localhost:8081/api/orders \
    -d '{"userId":"alice","totalAmount":50.00,"items":[{"productId":"p1","quantity":1,"price":50.0}]}'
{
  "orderId": "84d3...",
  "state": "CREATED",
  ...
}

# logs across all 4 services, abbreviated:
order-service     [SAGA] 84d3... -> CREATED, emitting inventory.reserve
inventory-service [INV]  84d3... reservation OK: RES-...
order-service     [SAGA] 84d3... -> INVENTORY_RESERVED, emitting payment.process
payment-service   [PAY]  84d3... payment OK: PAY-...
order-service     [SAGA] 84d3... -> PAYMENT_PROCESSED, emitting shipping.dispatch
shipping-service  [SHIP] 84d3... dispatched: TRK-...
order-service     [SAGA] 84d3... -> COMPLETED

$ curl -s http://localhost:8081/api/orders/84d3... | jq '.state, .trackingId'
"COMPLETED"
"TRK-..."
```

## What it does (failure path — compensation)

```
$ # Configure 100% payment failure
$ cd payment-service && PAYMENT_FAILURE_RATE=100 mvn spring-boot:run

$ curl -s -X POST http://localhost:8081/api/orders -d '{"userId":"bob","totalAmount":99}'

# logs:
order-service     [SAGA] ... -> CREATED, emitting inventory.reserve
inventory-service [INV]  ... reservation OK
order-service     [SAGA] ... -> INVENTORY_RESERVED, emitting payment.process
payment-service   [PAY]  ... payment FAILED (injected)
order-service     [SAGA] ... payment FAILED — compensating inventory
inventory-service [INV]  ... reservation RELEASED
order-service     [SAGA] ... -> CANCELLED

$ curl -s http://localhost:8081/api/orders/{id} | jq '.state, .failureReason'
"CANCELLED"
"payment declined (injected failure)"
```

---

## Architecture

```
            ┌─────────────────┐
            │  POST /orders   │
            └────────┬────────┘
                     │ HTTP
                     ▼
         ┌────────────────────────┐
         │     order-service      │  (orchestrator)
         │ • Saga state machine   │
         │ • Postgres (durable)   │
         └─┬────────────────────┬─┘
           │                    │
           │ commands           │ subscribes to result events
           ▼                    │
  ┌──────────────────┐          │
  │ inventory.reserve│          │ inventory.reserved | inventory.failed
  ├──────────────────┤          │ payment.processed  | payment.failed
  │ payment.process  │          │ shipping.completed | shipping.failed
  ├──────────────────┤          │
  │ shipping.dispatch│          │
  └─┬──────┬─────────┘          │
    │      │ │                  │
    ▼      ▼ ▼                  │
 ┌────┐ ┌────┐ ┌────┐           │
 │INV │ │PAY │ │SHIP│           │  each consumes its command, emits result
 └────┘ └────┘ └────┘ ──────────┘

 Compensation (on failure):
   payment.failed  → inventory.release → order.cancelled
   shipping.failed → payment.refund + inventory.release → order.cancelled
```

## What it proves

| Capability | Where |
|---|---|
| **SAGA orchestration** | `order-service/SagaOrchestrator` runs the state machine. Each event either advances the state or triggers compensation |
| **Durable state machine** | Order row in Postgres has `state` (enum), `reservationId`, `paymentId`, `trackingId`, `version` for optimistic locking. Saga recovers from crash mid-flow |
| **Idempotent transitions** | Each handler asserts the current state before advancing — re-delivered events are no-ops |
| **Compensation events** | `inventory.release` and `payment.refund` topics carry the original IDs so downstream services know what to undo |
| **Failure injection** | Each downstream service has a `*.failure-rate` knob (0-100%) to deterministically exercise the compensation path |
| **At-least-once + manual ack** | Listeners ack only after the side effect succeeds — re-delivery is safe because of the state-machine guard |
| **Multi-module Maven build** | One repo, 5 modules (`shared` + 4 services). Single `mvn verify` builds them all |

## Quickstart

```bash
git clone https://github.com/mohidev-tech/order-processing-system
cd order-processing-system

docker-compose up -d           # kafka + postgres

# Run all 4 services (4 terminals, OR scripted):
mvn -B install                  # builds shared first, then all services
( cd order-service     && mvn spring-boot:run ) &
( cd inventory-service && mvn spring-boot:run ) &
( cd payment-service   && mvn spring-boot:run ) &
( cd shipping-service  && mvn spring-boot:run ) &

# Happy path
curl -X POST http://localhost:8081/api/orders \
  -H 'Content-Type: application/json' \
  -d '{"userId":"alice","totalAmount":50}'

# Verify completion (takes ~1 second across all 4 services)
sleep 2
curl http://localhost:8081/api/orders | jq '.[] | {orderId, state, trackingId}'
```

## Exercising compensation

```bash
# Force 100% payment failures
(cd payment-service && PAYMENT_FAILURE_RATE=100 mvn spring-boot:run) &

curl -X POST http://localhost:8081/api/orders -d '{"userId":"x","totalAmount":1}' \
     -H 'Content-Type: application/json'
sleep 2
# Order ends in CANCELLED; inventory was released; payment was never charged.
```

## Repo layout

```
shared/                          Event POJOs + KafkaCommon config
                                 (consumer factory, producer factory, manual ack)
order-service/                   HTTP entry + Postgres state + SagaOrchestrator
                                 + SagaEventListener (subscribes to ALL result topics)
inventory-service/               inventory.reserve / inventory.release handlers
payment-service/                 payment.process / payment.refund handlers
shipping-service/                shipping.dispatch handler
```

## Topics

| Topic | Direction | Description |
|---|---|---|
| `inventory.reserve` | order → inventory | reserve stock for orderId |
| `inventory.reserved` | inventory → order | reservation succeeded; advances saga |
| `inventory.failed` | inventory → order | reservation failed; saga cancels |
| `inventory.release` | order → inventory | compensation; release a previous reservation |
| `payment.process` | order → payment | charge the customer |
| `payment.processed` | payment → order | charge succeeded; advances saga |
| `payment.failed` | payment → order | charge failed; saga triggers inventory compensation |
| `payment.refund` | order → payment | compensation; refund a previous charge |
| `shipping.dispatch` | order → shipping | ship the goods |
| `shipping.completed` | shipping → order | dispatched; saga completes |
| `shipping.failed` | shipping → order | dispatch failed; saga triggers payment + inventory compensation |
| `order.completed` | order → world | happy-path end |
| `order.cancelled` | order → world | failure-path end |

## Design choices

| Choice | Why |
|---|---|
| **Orchestrator over pure choreography** | A single state machine in one place is easier to reason about than 4 services each watching all the others. For high scale, can be migrated to choreography per-step |
| **State machine in Postgres** | The saga must recover from order-service crashes mid-flow. The Postgres `Order` row is the source of truth |
| **`@Version` on Order** | If two events for the same orderId race (e.g. duplicate delivery), optimistic locking forces one to retry against fresh state |
| **State-transition guards** | Every handler asserts the *current* state before advancing — re-delivered messages are no-ops, not double-processed |
| **Best-effort compensation** | `inventory.release` and `payment.refund` are fire-and-forget — order-service marks CANCELLED immediately. Production would wait for "released" / "refunded" acks |
| **Failure injection per service** | Each downstream has a `*.failure-rate` config knob (0-100%) — turn one to 100 to deterministically reproduce a saga failure path |

## Limitations (deliberately out of scope)

- **No replay tool.** A real saga needs an admin endpoint to re-fire stuck sagas after fixes.
- **No outbox pattern.** The orchestrator does `repository.save()` + `kafkaTemplate.send()` in the same `@Transactional` method — if Kafka is down the transaction rolls back; if Kafka succeeds but the DB commit fails, the event is sent without state change. Production: use the outbox pattern (Debezium etc.).
- **Inventory release is fire-and-forget.** We immediately mark CANCELLED. Real systems wait for an `inventory.released` ack before considering the saga truly closed.
- **No distributed tracing.** Add OpenTelemetry + Tempo/Jaeger so you can see the full saga in one trace span per orderId.

## Contributing

PRs welcome. See [CONTRIBUTING.md](CONTRIBUTING.md). Security issues: [SECURITY.md](SECURITY.md).

## License

Apache 2.0 — see [LICENSE](LICENSE) and [NOTICE](NOTICE).
