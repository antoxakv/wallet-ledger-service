# Wallet Ledger Service

gRPC service for wallet operations: `Debit`, `Credit`, and `GetBalance`.

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

Seed data is enabled by adding `compose.seed.yaml` to the Docker Compose command. Migration file: `flyway/src/main/resources/db/seed/R__seed_wallets.sql`.

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