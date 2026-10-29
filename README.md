# MDS orders: event-driven order-api and inventory-service

Two independent Spring Boot services that communicate only through RabbitMQ:

- **order-api** (port 8080): accepts orders over REST and publishes `OrderPlaced`. It then updates each order's
  status when the inventory result arrives.
- **inventory-service** (port 8081): consumes `OrderPlaced`, reserves stock if enough is available,
  and publishes `InventoryReservationResult` (`RESERVED`, or `REJECTED` with a reason).

The focus is on reliable integration rather than business logic. See [Design decisions](#design-decisions).

```
            POST /orders
                 │
                 ▼
┌──────────────────────────┐   OrderPlaced    ┌───────────────┐  order.placed   ┌──────────────────────────┐
│ order-api                │ ───────────────▶ │ orders.       │ ──────────────▶ │ inventory-service        │
│  orders + outbox         │                  │ exchange      │                 │  stock, reservations     │
│  (order_db)              │                  └───────────────┘                 │  + outbox (inventory_db) │
│                          │   Reservation    ┌───────────────┐ reservation.    │                          │
│                          │ ◀─────────────── │ inventory.    │ ◀────────────── │                          │
└──────────────────────────┘   Result         │ exchange      │ result          └──────────────────────────┘
                                              └───────────────┘
```

## Tech stack

Java 21, Spring Boot 4, Spring AMQP, RabbitMQ 4.1, PostgreSQL 17, Flyway, Micrometer/Prometheus,
JUnit 5 + Testcontainers, Docker Compose.

## Running

Requirements: Docker with Compose (or Podman Compose).

```bash
docker compose up -d --build
docker compose ps                     # wait until all four containers are "healthy"
```

| Service           | URL                                                                   |
|-------------------|-----------------------------------------------------------------------|
| order-api         | http://localhost:8080 (health: `/actuator/health`)                    |
| inventory-service | http://localhost:8081 (health: `/actuator/health`)                    |
| RabbitMQ UI       | http://localhost:15672 (user `app`, password `app`)                   |
| Metrics           | `/actuator/prometheus` on both services                               |

Stop with `docker compose down`. Add `-v` to also delete the database volume; on the next start
`docker/postgres/init.sql` runs again and Flyway recreates the schema and seed data.

All credentials in this repository are local development values only. In production they would come from a
secrets manager.

## Try it

Inventory starts with seed stock: `item-1` = 10, `item-2` = 5, `item-3` = 0.

```bash
# Enough stock -> RESERVED
curl -XPOST localhost:8080/orders -H 'Content-Type: application/json' \
     -H 'X-Correlation-Id: demo-1' -d '{"orderId":"ord1","itemId":"item-1","quantity":2}'

# No stock -> REJECTED / INSUFFICIENT_STOCK
curl -XPOST localhost:8080/orders -H 'Content-Type: application/json' \
     -d '{"orderId":"ord2","itemId":"item-3","quantity":1}'

# Unknown item -> REJECTED / UNKNOWN_ITEM
curl -XPOST localhost:8080/orders -H 'Content-Type: application/json' \
     -d '{"orderId":"ord3","itemId":"ghost","quantity":1}'

curl localhost:8080/orders/ord1           # status: PENDING, then RESERVED about a second later
curl localhost:8081/reservations/ord1     # inventory's record of the same order
curl localhost:8081/stock-items          # item-1 is now 8
```

inventory-service's REST API is read-only and exists only to inspect results; its stock changes only through
events.

`POST /orders` returns `202 Accepted` with status `PENDING`, because the reservation happens asynchronously.
Sending the same `orderId` again returns the stored order and does not publish a second event.

To follow one order through both services:

```bash
docker compose logs -f order-api inventory-service                  # live logs of both
docker compose logs order-api inventory-service | grep demo-1       # one order, by correlation ID
```

## Tests

Each service has unit tests and integration tests that run against real Postgres and RabbitMQ via
Testcontainers (Docker must be running):

```bash
cd order-api && ./gradlew test
cd inventory-service && ./gradlew test
```

## Design decisions

**At-least-once delivery, made safe by idempotent consumers.** Each side assumes messages can be
duplicated, and neither assumes it can be lost.

- **Transactional outbox (both services).** The state change and the outgoing event are written in the same
  database transaction. A scheduled relay then publishes them. That means there is never a stored order
  without its event, or an event without its order, even if the service or the broker crashes between the two steps.
- **Publisher confirms + mandatory routing.** An outbox row is marked as sent only after RabbitMQ acks it and
  routes it to a queue. If the consumer's queue does not exist yet, the event stays in the outbox and is
  retried with exponential backoff, so the services can start in any order.
- **Multiple relay instances.** Outbox rows are claimed with `FOR UPDATE SKIP LOCKED`, so several instances
  can publish in parallel without sending the same event twice.
- **Idempotency.** `POST /orders` is idempotent on `orderId`. Inventory records one reservation per order
  (primary key), so a redelivered `OrderPlaced` never reserves stock twice. order-api ignores a result for an
  order that already has a final status.
- **No overselling.** Stock is checked and decremented in a single conditional `UPDATE`, so concurrent orders
  for the same item can't oversell it.
- **Retries and dead-letter queues.** Invalid messages (wrong type, unsupported `schemaVersion`, malformed
  JSON) go straight to a DLQ. Transient failures are retried with backoff, then dead-lettered. The DLQs are
  `inventory.order-placed.dlq` and `order-api.reservation-results.dlq`.
- **Versioned message contract.** Messages carry `type`, a `schemaVersion` header, `messageId` and
  `correlationId`. Consumers ignore unknown fields, so adding a field doesn't need a coordinated release, and
  they reject versions they don't understand instead of guessing.
- **Observability.** The correlation ID from the HTTP request travels with the messages and appears in every
  log line of both services. Prometheus metrics cover published and failed events, outbox backlog and age,
  and reservation outcomes.

More detail per service: [order-api/README.md](order-api/README.md) and
[inventory-service/README.md](inventory-service/README.md).

## Assumptions and simplifications

- **Database per service, one Postgres instance.** Each service has its own database (`order_db`,
  `inventory_db`) and its own user, and neither reads the other's data. Sharing one Postgres container is a
  local-setup simplification. In production each service would get its own instance.
- **Postgres instead of in-memory state.** The task allows in-memory storage, but the outbox needs the state
  change and the event in one transaction.
- **One item per order**, as in the example payload. Reserved stock is never released (no cancellation flow).
- **Contracts are duplicated, not shared as a library**, so the services can be built and released independently.
- **Not implemented (next steps):** cleanup of published outbox rows, a DLQ replay tool and alerting on DLQ
  depth, authentication on the APIs, and contract tests that run both services together.
