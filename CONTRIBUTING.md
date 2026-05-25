# Contributing to order-processing-system

PRs welcome.

## Local development loop

```bash
docker-compose up -d
mvn -B install                  # builds all 5 modules
( cd order-service     && mvn spring-boot:run ) &
( cd inventory-service && mvn spring-boot:run ) &
( cd payment-service   && mvn spring-boot:run ) &
( cd shipping-service  && mvn spring-boot:run ) &
```

## Where to make changes

| You want to... | Edit |
|---|---|
| Add a new step to the saga (e.g. tax calculation) | Add a new event in `shared/Events.java` + new topic constant + new service module + new state in `Order.SagaState` + new handler in `SagaOrchestrator` |
| Change a compensation rule | `SagaOrchestrator.onXxxFailed` — that's where the backwards compensation events are emitted |
| Add a new failure injection mode | `*.failure-rate` config + the corresponding service's main method |
| Change idempotency model | The state guards in `SagaOrchestrator.handle*` — they're the only thing preventing duplicate-delivery double-processing |

## PR checklist

- [ ] `mvn -B verify` passes for all modules.
- [ ] New saga states have a state guard at every transition.
- [ ] New events are in `shared/Events.java` (not duplicated per service).
- [ ] New topic names are constants in `shared/Topics.java`.
- [ ] Failure-injection knobs default to **0** (off).

## License

By submitting a PR you agree the contribution is Apache 2.0. See [LICENSE](LICENSE) and [NOTICE](NOTICE).
