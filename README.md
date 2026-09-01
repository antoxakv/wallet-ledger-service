# Wallet Ledger Service

gRPC service for wallet operations: `Debit`, `Credit`, and `GetBalance`.

The service maintains wallet balances and an immutable transaction ledger, prevents double-spending under concurrent requests, provides idempotent transaction processing, and publishes wallet update events to RabbitMQ using the Transactional Outbox pattern.

## Architecture and Design Decisions

### Concurrency and Double-Spending Protection

Wallet operations for the same wallet are serialized to prevent concurrent balance updates and double-spending.

A Redis-based distributed lock is acquired before executing a debit or credit operation. If the lock is temporarily held by another request, the service retries lock acquisition for a bounded period instead of failing immediately. This allows short-lived concurrent requests for the same wallet to wait for the previous operation to complete and still have a chance to execute successfully.

Redis is not the final consistency guarantee. Balance modifications are performed inside a database transaction and the wallet row is locked using PostgreSQL row-level locking (`SELECT ... FOR UPDATE`).

PostgreSQL remains the source of truth and provides the final protection against concurrent balance modifications. If Redis is temporarily unavailable, wallet operations fall back to database locking.

This provides two layers of coordination:

1. Redis serializes concurrent operations for the same wallet before they reach the database. Lock acquisition is retried for a bounded period to allow short-lived competing operations to complete instead of immediately rejecting the request. PostgreSQL row-level locking remains the final consistency guarantee.
2. PostgreSQL row-level locking provides the final consistency guarantee.

Operations for different wallet can be processed concurrently, while operations modifying the same wallet are intentionally serialized.

### Idempotency

Every debit and credit request contains a unique `transaction_id`.

Before executing a financial operation, the service checks whether the transaction has already been processed. If the same `transaction_id` is received again with the same wallet, operation type, and amount, the previously stored result is returned without executing the financial movement again.

If the same `transaction_id` is reused with different request parameters, the request is treated as a conflict.

A database uniqueness constraint provides the final protection against concurrent requests attempting to create the same transaction simultaneously.

Failed debit attempts caused by insufficient funds are also stored in the immutable ledger. This ensures that retrying the same transaction later returns the original failed result instead of unexpectedly executing the debit after the wallet balance has changed.

### Immutable Ledger

Every processed wallet operation is appended to `wallet_ledger`.

Ledger records are never updated or deleted. The ledger stores the result of processed wallet transactions, including successful operations and deterministic failed debit attempts.

The current wallet balance is stored separately in the `wallet` table for efficient reads and updates.

### Transactional Outbox

Wallet update events are not published directly to RabbitMQ from the wallet database transaction.

A successful wallet operation creates an outbox record in the same PostgreSQL transaction as the wallet balance update and ledger entry. A separate publisher reads pending outbox records and publishes `WalletUpdatedEvent` messages to RabbitMQ.

This avoids the dual-write problem where the database transaction succeeds but the application fails before the corresponding RabbitMQ event is published.

The database transaction therefore atomically persists:

1. the updated wallet balance;
2. the immutable ledger entry;
3. the outbox event.

PostgreSQL remains the authoritative source of wallet state.

### Message Delivery Semantics

RabbitMQ event delivery is **at-least-once**.

A failure can occur after an event has successfully been published to RabbitMQ but before the corresponding outbox record is marked as published. In that case, the event can be published again.

Downstream consumers should therefore process wallet update events idempotently using the transaction id.

### Locking Trade-off

Lock acquisition does not fail immediately when another request currently owns the Redis lock. The service retries acquisition for a bounded period so that a request can wait for a short-running wallet operation to finish and then proceed normally.

The waiting period is bounded, so requests cannot wait indefinitely.

The Redis lock is an additional distributed coordination mechanism; PostgreSQL row-level locking remains the final correctness guarantee.

## Run Modes

### Infrastructure only + local application

Starts PostgreSQL, Flyway, RabbitMQ, Redis, pgAdmin, and Redis Insight. The Java application is started locally from the IDE or Gradle.

```bash
docker compose -f compose.infra.yaml up --build -d
```

Local application startup:

```bash
TZ=UTC ./gradlew bootRun
```

Windows PowerShell:

```powershell
$env:TZ = 'UTC'
.\gradlew.bat bootRun
```

The gRPC service is available at `localhost:9090`.

### Infrastructure only with seed wallets

```bash
docker compose -f compose.infra.yaml -f compose.seed.yaml up --build -d
```

### Full Docker startup

Starts infrastructure and `wallet-ledger-service-app`.

```bash
docker compose -f compose.yaml up --build -d
```

gRPC endpoint: `localhost:9090`.

### Full Docker startup with seed wallets

```bash
docker compose -f compose.yaml -f compose.seed.yaml up --build -d
```

## UI Clients

### pgAdmin

URL: `http://localhost:5050`

pgAdmin login:

```text
Email: wallet-ledger-service@local.dev
Password: wallet-ledger-service
```

PostgreSQL connection inside pgAdmin:

```text
Host: postgres
Port: 5432
Database: wallet-ledger-service
Username: postgres
Password: postgres
```

Application PostgreSQL user:

```text
Username: wallet-ledger-service
Password: wallet-ledger-service
Schema: wallet-ledger-service
```

### RabbitMQ Management

URL: `http://localhost:15672`

Admin user:

```text
Username: guest
Password: guest
```

Application user:

```text
Username: wallet-ledger-service
Password: wallet-ledger-service
```

RabbitMQ entities:

```text
Exchange: wallet.event
Queue: wallet-event
```

### Redis Insight

URL: `http://localhost:5540`

Redis connection from Redis Insight container/network:

```text
Host: redis
Port: 6379
Username: default
Password: empty
```

## Seed Wallets

Seed data is enabled by adding `compose.seed.yaml` to the Docker Compose command.

Migration file: `flyway/src/main/resources/db/seed/R__seed_wallets.sql`.

| wallet_id | balance |
| --- | ---: |
| `00000000-0000-0000-0000-000000000001` | 100000 |
| `00000000-0000-0000-0000-000000000002` | 250000 |
| `00000000-0000-0000-0000-000000000003` | 500000 |
| `00000000-0000-0000-0000-000000000004` | 750000 |
| `00000000-0000-0000-0000-000000000005` | 1000000 |
| `00000000-0000-0000-0000-000000000006` | 1500000 |
| `00000000-0000-0000-0000-000000000007` | 2000000 |
| `00000000-0000-0000-0000-000000000008` | 50000 |
| `00000000-0000-0000-0000-000000000009` | 10000 |
| `00000000-0000-0000-0000-000000000010` | 0 |

## Transaction IDs for Manual Debit/Credit Requests

Each `transaction_id` can be used only once for one financial operation. A retry with the same wallet, operation type, and amount returns the original result.

```text
10000000-0000-0000-0000-000000000001
10000000-0000-0000-0000-000000000002
10000000-0000-0000-0000-000000000003
10000000-0000-0000-0000-000000000004
10000000-0000-0000-0000-000000000005
10000000-0000-0000-0000-000000000006
10000000-0000-0000-0000-000000000007
10000000-0000-0000-0000-000000000008
10000000-0000-0000-0000-000000000009
10000000-0000-0000-0000-000000000010
10000000-0000-0000-0000-000000000011
10000000-0000-0000-0000-000000000012
10000000-0000-0000-0000-000000000013
10000000-0000-0000-0000-000000000014
10000000-0000-0000-0000-000000000015
10000000-0000-0000-0000-000000000016
10000000-0000-0000-0000-000000000017
10000000-0000-0000-0000-000000000018
10000000-0000-0000-0000-000000000019
10000000-0000-0000-0000-000000000020
```

## gRPC

Proto file: `src/main/proto/wallet_ledger_service.proto`.