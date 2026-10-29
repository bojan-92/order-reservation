# inventory-service

Consumes `OrderPlaced` events from `order-api`, reserves stock, and publishes an
`InventoryReservationResult` (`RESERVED` / `REJECTED` + reason) back to `order-api`.

## Running

From the repository root (with order-api, Postgres and RabbitMQ):

```bash
docker compose up -d --build
```

| What            | Where                                     |
|-----------------|-------------------------------------------|
| REST API        | http://localhost:8081                     |
| Health          | http://localhost:8081/actuator/health     |
| Metrics         | http://localhost:8081/actuator/prometheus |
| RabbitMQ UI     | http://localhost:15672 (`app` / `app`)    |

Seed stock (Flyway `V4`): `item-1` = 10, `item-2` = 5, `item-3` = 0.

```bash
curl -XPOST localhost:8080/orders -H 'Content-Type: application/json' \
     -H 'X-Correlation-Id: demo-1' -d '{"orderId":"ord1","itemId":"item-1","quantity":2}'
curl localhost:8080/orders/ord1          # PENDING -> RESERVED
curl localhost:8081/reservations/ord1    # inventory's view of the same order
curl localhost:8081/stock-items         # item-1 is now 8
curl localhost:8081/stock-items/item-1
```

The REST API is read-only, for inspecting results. Stock changes only through `OrderPlaced` events.
To change starting stock, edit `V4__seed_stock.sql` and restart with `docker compose down -v`.

Tests (Docker is required for Testcontainers): `./gradlew test`

## Message flow

```
order-api ──OrderPlaced──▶ orders.exchange ──order.placed──▶ inventory.order-placed ──▶ inventory-service
                                                                   │ (invalid / retries exhausted)
                                                                   ▼
                                                     inventory.dlx ──▶ inventory.order-placed.dlq

inventory-service ──InventoryReservationResult──▶ inventory.exchange ──reservation.result──▶ order-api
```

Contract: JSON body, AMQP `type` = event name, `schemaVersion` header, `messageId`, `correlationId`.

## Reliability decisions

**Idempotent consumer.** The broker delivers at least once, so the same `OrderPlaced` can arrive more than
once. Each order gets exactly one row in `reservations` (primary key `order_id`). A redelivery finds the row
and is acknowledged without touching stock. If two copies are processed concurrently, both pass the existence
check, but only one INSERT succeeds; the other fails on the primary key, its transaction (including the stock
decrement) rolls back, and on retry it is recognised as a duplicate. `InventoryIntegrationTest` covers this
case. While building it, the test caught that JPA `merge` on an assigned ID was overwriting the row instead of
failing; `@Version` on `Reservation` makes Spring Data issue a real INSERT.

**No overselling.** Stock is reserved with a single conditional statement
(`UPDATE ... SET available = available - q WHERE item_id = ? AND available >= q`) plus a `CHECK (available >= 0)`.
The row lock serialises concurrent orders for the same item, so the listener can safely run with concurrency > 1.
`concurrentOrdersForSameItem_neverOversell` sends 20 orders against stock of 5 and expects exactly 5 reservations.

**Transactional outbox for the result.** The stock change, the reservation and the result event are committed
in one DB transaction. A relay publishes the outbox with publisher confirms and `mandatory=true`, and marks a row
as sent only after the broker acks it *and* routes it to a queue. A crash at any point means either nothing
happened (the message is redelivered) or the result is in the outbox and will be published. This uses the same
pattern and relay code as order-api, and `FOR UPDATE SKIP LOCKED` lets several instances run the relay in parallel.

**Poison messages vs. transient failures.** Wrong `type`, unsupported `schemaVersion`, malformed JSON or invalid
fields throw `InvalidEventException`, which skips retries and goes straight to the DLQ. Other errors (DB down,
duplicate-key race) are retried with exponential backoff (3 retries, 1s → 10s) and then dead-lettered. Nothing
is requeued forever.

**Business rejection is not an error.** Insufficient stock (`INSUFFICIENT_STOCK`) or an unknown item
(`UNKNOWN_ITEM`) is a normal outcome. The order is processed successfully and a `REJECTED` result is published.

**Tolerant reader.** Unknown JSON fields are ignored, so the producer can add fields without a coordinated
release. A breaking change gets a new `schemaVersion`, which this consumer rejects explicitly rather than
misinterpreting.

**Observability.** The correlation ID from order-api's HTTP request is carried through the message, logged in
MDC on both services, and copied onto the result event, so `grep <correlationId>` follows one order through the
whole system. Metrics: `inventory.reservations{status}`, `inventory.orders.duplicate`, `outbox.pending`,
`outbox.oldest.age`, `outbox.published`, `outbox.publish.failures`.

## Assumptions and simplifications

- **Postgres instead of in-memory.** The task allows in-memory state, but atomic "reserve + record + publish"
  needs a transaction, and Postgres was already in the stack (`inventory_db` in `docker/postgres/init.sql`).
- **One item per order**, matching the order-api payload. Reservations are never released (no cancel flow).
- **Contracts are duplicated, not shared as a library.** The services share a message schema, not code, so
  they can be built and released independently.
- **Exchanges are declared by both services**, so start order doesn't matter. If order-api publishes before
  this service has declared its queue, order-api's relay gets an unroutable return, keeps the event in its
  outbox and retries with backoff. The event is delivered as soon as inventory-service is up.
- **Not done (would be next):** cleanup of published outbox rows; a DLQ replay tool or alerting on DLQ depth;
  auth on the REST API; Testcontainers-based contract tests run against both services together.
