# Stock Market Simulator

> **Recruitment task** for the Software Development Intern position at [Remitly](https://www.remitly.com) (Kraków, 2026).

---

A simplified stock market REST API built with **Spring Boot 3**, backed by **Redis** for shared state, and deployed as three application instances behind an **Nginx** load balancer — providing high availability even when individual instances are killed via `POST /chaos`.

## Architecture

```
Client
  │
  ▼
Nginx (load balancer, round-robin)
  ├── app1 (Spring Boot)
  ├── app2 (Spring Boot)
  └── app3 (Spring Boot)
          │
          ▼
       Redis (shared state: bank, wallets, audit log)
```

All state lives in Redis, so any instance can handle any request. When one instance is killed by `POST /chaos`, Nginx routes traffic to the remaining two. With `restart: always`, Docker also brings the killed instance back up automatically.

## Requirements

- Docker with Docker Compose v2
- No Java installation needed — the app is compiled inside Docker via a multi-stage build

## Starting the application

### Linux / macOS
```bash
chmod +x start.sh
./start.sh <PORT>

# Example:
./start.sh 9000
```

### Windows
```bat
start.bat <PORT>

REM Example:
start.bat 9000
```

The API will be available at `http://localhost:<PORT>`.

## Stopping

```bash
docker compose down
```

---

## API Reference

### `POST /wallets/{wallet_id}/stocks/{stock_name}`
Buy or sell a single unit of a stock. Creates the wallet automatically if it doesn't exist yet.

**Body:** `{"type": "buy"}` or `{"type": "sell"}`

| Condition | Status |
|-----------|--------|
| Success | 200 |
| Stock doesn't exist in the bank | 404 |
| Buy — bank has 0 of this stock | 400 |
| Sell — wallet has 0 of this stock | 400 |

---

### `GET /wallets/{wallet_id}`
Returns the current state of a wallet.

**Response:**
```json
{"id": "wallet1", "stocks": [{"name": "AAPL", "quantity": 5}]}
```

---

### `GET /wallets/{wallet_id}/stocks/{stock_name}`
Returns the quantity of a specific stock held in a wallet. Returns 404 if the stock doesn't exist in the bank.

**Response:** `5`

---

### `GET /stocks`
Returns current bank inventory.

**Response:**
```json
{"stocks": [{"name": "AAPL", "quantity": 100}]}
```

---

### `POST /stocks`
Replaces the entire bank state atomically.

**Body:**
```json
{"stocks": [{"name": "AAPL", "quantity": 100}, {"name": "GOOG", "quantity": 50}]}
```

**Response:** `200`

---

### `GET /log`
Returns the full audit log of successful wallet operations (bank operations excluded), in order of occurrence.

**Response:**
```json
{"log": [{"type": "buy", "wallet_id": "w1", "stock_name": "AAPL"}]}
```

---

### `POST /chaos`
Kills the instance that handles this request. The system remains available via the remaining instances.

---

## Design decisions

**Redis as the single source of truth** — no in-memory state in the application, making horizontal scaling trivial and ensuring consistency across all instances.

**Optimistic locking with WATCH/MULTI/EXEC** — buy and sell operations use Redis transactions with a retry loop. The WATCH is issued inside the same `SessionCallback` as the MULTI/EXEC, guaranteeing atomicity even under concurrent requests hitting different app instances simultaneously.

**Three app instances behind Nginx** — satisfies the high-availability requirement: killing one still leaves two healthy instances serving traffic. `restart: always` ensures the killed instance recovers automatically.

**`proxy_next_upstream`** — if the upstream app process dies mid-request (e.g. during `POST /chaos`), Nginx retries on the next healthy instance, making the failure transparent to the caller.

**Multi-stage Dockerfile** — the builder stage compiles the JAR using Gradle; the final image contains only the JRE and the JAR, keeping the image size minimal.
