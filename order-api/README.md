# order-api

Accepts orders over REST, publishes an `OrderPlaced` event to inventory-service through a transactional outbox,
and updates each order's status when the `InventoryReservationResult` comes back.

## Running

With the whole system, from the repository root:

```bash
docker compose up -d --build
```

Locally from the IDE or Gradle, with only the infrastructure in Docker:

```bash
docker compose up -d postgres rabbitmq     # from the repository root
./gradlew bootRun                          # from order-api/
```

Stop the `order-api` container first (`docker compose stop order-api`), because both use port 8080.

| What        | Where                                     |
|-------------|-------------------------------------------|
| REST API    | http://localhost:8080/orders              |
| Health      | http://localhost:8080/actuator/health     |
| Metrics     | http://localhost:8080/actuator/prometheus |
| Swagger UI  | http://localhost:8080/swagger-ui.html     |
| OpenAPI     | http://localhost:8080/v3/api-docs         |
| RabbitMQ UI | http://localhost:15672 (`app` / `app`)    |

Tests (Docker is required for Testcontainers): `./gradlew test`

## REST API

### `POST /orders`

```bash
curl -i -XPOST localhost:8080/orders -H 'Content-Type: application/json' \
     -H 'X-Correlation-Id: demo-1' -d '{"orderId":"o-1","itemId":"item-1","quantity":2}'
```

```json
{"orderId":"o-1","itemId":"item-1","quantity":2,"status":"PENDING","statusReason":null,
 "createdAt":"2026-10-29T10:00:00.000Z","updatedAt":"2026-10-29T10:00:00.000Z"}
```

| Status | When |
|--------|------|
| `202 Accepted` | Order stored, `OrderPlaced` queued in the outbox. Also returned for a repeated `orderId`, with the stored order. |
| `400 Bad Request` | Blank `orderId` or `itemId`, either one longer than 64 characters, or `quantity` missing or not positive. |
| `409 Conflict` | Another request with the same `orderId` is being created at the same moment. Retrying returns the stored order. |

The response always includes `X-Correlation-Id`: the one sent by the client if it is valid, otherwise a newly
generated one.

### `GET /orders/{orderId}`

Returns the order with its current status (`200`), or `404` if it doesn't exist. The status is `PENDING` until
inventory-service answers, then `RESERVED`, or `REJECTED` with a `statusReason` such as `INSUFFICIENT_STOCK`.

Errors use RFC 9457 problem details (`application/problem+json`).

## Message flow

```
POST /orders ──▶ orders + outbox_events (one transaction)
                        │
                 OutboxRelay (every 1s)
                        ▼
                 orders.exchange ──order.placed──▶ inventory-service

inventory-service ──▶ inventory.exchange ──reservation.result──▶ order-api.reservation-results ──▶ ReservationResultListener
                                                                        │ (invalid / retries exhausted)
                                                                        ▼
                                                         order-api.dlx ──▶ order-api.reservation-results.dlq
```

`OrderPlaced` (schema version 1):

```json
{"orderId":"o-1","itemId":"item-1","quantity":2,"occurredAt":"2026-10-29T10:00:00.000Z"}
```

Message properties: `type=OrderPlaced`, header `schemaVersion=1`, `messageId` (outbox row id, stable across
redeliveries), `correlationId`, persistent delivery mode, JSON content type.

This service declares the exchanges and its own result queue and DLQ. The `order.placed` queue belongs to
inventory-service, which declares it.

## Reliability decisions

**Transactional outbox.** The order and its `OrderPlaced` event are written in one database transaction. There
is never an accepted order whose event is lost, or an event for an order that was rolled back.

**Publish only counts once the broker has it.** `OutboxRelay` sends with publisher confirms and
`mandatory=true`. A row is marked published only when RabbitMQ acks it and routes it to a queue. A nack, a
timeout, or "no queue bound" (inventory-service not started yet) leaves the row in the outbox for the next
attempt. Delivery is therefore at-least-once, and consumers de-duplicate by `messageId` or `orderId`.

**Backoff instead of hammering.** A failed publish pauses the relay with exponential backoff
(1s, 2s, 4s … capped at 60s), and each run stops at the first failure, which keeps events in order. Failures
are usually broker-wide, so retrying the rest of the batch would fail the same way. One WARN line is logged per
attempt, and one INFO line when publishing recovers.

**Several instances.** Each run claims its batch with `SELECT … FOR UPDATE SKIP LOCKED` in one transaction, so
two instances never publish the same event concurrently (`OutboxLockingIntegrationTest`).

**Idempotent `POST /orders`.** `orderId` is the primary key and the idempotency key. A repeated `orderId`
returns the stored order and publishes nothing, so a client retry never reserves stock twice. Two simultaneous
requests with the same new `orderId` race on the primary key: one wins, the loser's transaction (including its
outbox row) rolls back, and it gets a `409`.

**Consuming results.** Wrong `type`, unsupported `schemaVersion`, malformed JSON, missing fields or an unknown
order throw `InvalidEventException`, which skips retries and goes straight to the DLQ. Other errors (for example
the database is down) are retried 3 times with backoff (1s → 10s), then dead-lettered. A result for an order
that is already `RESERVED` or `REJECTED` is ignored, so redelivered results are harmless.

**Correlation ID.** Taken from `X-Correlation-Id` (or generated), put in the logging MDC, stored on the outbox
row and sent as the AMQP `correlationId`. The listener binds the ID from incoming results, so
`grep <correlationId>` follows one order through both services. Incoming IDs must match `[A-Za-z0-9._-]{1,64}`;
anything else is replaced, which prevents log injection through the header.

## Observability

| Metric | Meaning |
|--------|---------|
| `outbox_pending` | Events waiting to be published |
| `outbox_oldest_age_seconds` | How long the oldest unpublished event has waited (the best "publishing is stuck" alert) |
| `outbox_published_total`, `outbox_publish_failures_total` | Publish throughput and failures |
| `http_server_requests_seconds_*` | Latency and status codes of the REST API |

Outbox rows also keep `attempts` and `last_error` for debugging.

## Configuration

Defaults are in `src/main/resources/application.yml`; any of them can be overridden with environment variables
(for example `SPRING_DATASOURCE_URL`, `SPRING_RABBITMQ_HOST`, `APP_OUTBOX_MAX_BACKOFF_MS`).

| Property | Default | Purpose |
|----------|---------|---------|
| `app.outbox.relay-interval-ms` | `1000` | Delay between relay runs, also the first retry delay |
| `app.outbox.max-backoff-ms` | `60000` | Upper bound for the retry delay |
| `app.outbox.batch-size` | `100` | Events claimed per run |
| `app.outbox.confirm-timeout-ms` | `5000` | Wait for the broker's confirm per event |
| `app.messaging.*` | see `application.yml` | Exchange, queue and routing key names |

## Tests

| Test | Kind | Covers |
|------|------|--------|
| `OrderServiceTest` | unit | Order and outbox event saved together, idempotent repeat, result handling, duplicate results |
| `ReservationResultListenerTest` | unit | Rejecting wrong type, schema version, malformed JSON, unknown status, missing fields |
| `OutboxRelayTest` | unit | Confirm, nack, unroutable message, stop at first failure, backoff |
| `ApiIntegrationTest`, `OrderFlowIntegrationTest` | Testcontainers | HTTP → DB → broker with headers and correlation ID, idempotency, 400/404, result → status, DLQ |
| `OutboxLockingIntegrationTest` | Testcontainers | Two concurrent relays never claim the same events |

## Assumptions and simplifications

- **`orderId` is an opaque client-supplied string.** No format is assumed; it is limited to 64 characters to
  fit the column. It is the only idempotency key: a repeated `orderId` with a different `itemId` or `quantity`
  returns the stored order unchanged instead of an error.
- **`409` on a concurrent duplicate** instead of returning the stored order directly. The losing transaction is
  aborted by Postgres, so answering with the stored order would need a second read after the rollback. A client
  retry already gets that result.
- **One event type, one event per order.** Strict global ordering isn't needed. With several relay instances,
  newer events can overtake older ones that another instance has locked. That is harmless while each order
  produces a single event.
- **Row locks are held while waiting for confirms**, up to `confirm-timeout-ms` per event in the worst case.
  This only delays other relay instances; inserting new orders doesn't touch locked rows.
- **A stuck event blocks the ones behind it.** Payloads are serialized from validated requests and the broker
  doesn't inspect them, so realistic failures are broker-wide. A manually corrupted row would block the relay;
  the fix would be a `FAILED` state after N attempts. `outbox_oldest_age_seconds` makes this visible.
- **Not done (would be next):** cleanup of published outbox rows, a DLQ replay tool and alerting on DLQ depth,
  authentication, JSON (structured) logs for log aggregation.
