# Security policy

## Reporting a vulnerability

→ [Report a vulnerability](https://github.com/mohidev-tech/order-processing-system/security/advisories/new)

I aim to acknowledge within **72 hours** and fix critical issues within **30 days**.

## Known caveats (not vulnerabilities)

| Caveat | Why |
|---|---|
| `docker-compose.yml` ships plaintext postgres password | Demo/dev only. Use Vault or K8s Secrets in production. |
| Kafka has no SASL/TLS | Demo/dev. Enable SASL/SCRAM + TLS in production. |
| Save-then-publish is NOT in an outbox | `SagaOrchestrator.startSaga` is `@Transactional`, but a Kafka failure with a successful DB commit (or vice versa) creates inconsistency. Use Debezium / outbox pattern for production. |
| Compensation is fire-and-forget | Inventory release / payment refund have no ack path back to the orchestrator. Real systems persist the compensation request and retry on failure. |
| Failure-injection knobs are exposed via config | `*.failure-rate` envs are intended for testing — keep them at 0 in production deployments. |

## In scope

- A saga that gets stuck in an invalid state (e.g. PAYMENT_PROCESSED but no payment was made).
- A duplicate-delivery scenario that bypasses the state guards.
- A compensation event that misses (inventory released or payment refunded for the wrong orderId).
- An admin endpoint discovered that lets an attacker advance a saga to COMPLETED without going through the actual services.

## Out of scope

- "There's no auth on the controller" — by design for the demo.
- "Failure rates are configurable" — see caveat table.
