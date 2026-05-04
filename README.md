# Stock Market Simulator

A simplified stock market REST API built with **Spring Boot 4**, backed by **Redis** for shared state, and deployed as three application instances behind an **Nginx** load balancer — providing high availability even when individual instances are killed via `POST /chaos`.

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

All state lives in Redis, so any instance can handle any request. When one instance is killed by `POST /chaos`, Nginx routes traffic to the remaining two (with `restart: always`, Docker brings the killed instance back up automatically).

## Requirements

- Docker (with Docker Compose v2)
- No Java installation needed — the app is built inside Docker

## Starting the application

### Linux / macOS
```bash
./start.sh <PORT>
# Example:
./start.sh 9000
```

### Windows
```bat
start.bat <PORT>
# Example:
start.bat 9000
```

The API will be available at `http://localhost:<PORT>`.

## Stopping

```bash
docker compose down
```

## API Reference

### `POST /wallets/{wallet_id}/stocks/{stock_name}`
Buy or sell a single unit of a stock.

**Body:** `{"type": "buy"}` or `{"type": "sell"}`

| Condition | Status |
|-----------|--------|
| Success | 200 |
| Stock doesn't exist in bank | 404 |
| Buy — bank has 0 of this stock | 400 |
| Sell — wallet has 0 of this stock | 400 |

If the wallet doesn't exist it is created automatically on first operation.

---

### `GET /wallets/{wallet_id}`
Returns the current state of a wallet.

**Response:**
```json
{"id": "wallet1", "stocks": [{"name": "AAPL", "quantity": 5}]}
```

---

### `GET /wallets/{wallet_id}/stocks/{stock_name}`
Returns quantity of a specific stock in a wallet.

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
Replaces the entire bank state.

**Body:**
```json
{"stocks": [{"name": "AAPL", "quantity": 100}, {"name": "GOOG", "quantity": 50}]}
```

**Response:** 200

---

### `GET /log`
Returns the full audit log of successful wallet operations (bank operations excluded).

**Response:**
```json
{"log": [{"type": "buy", "wallet_id": "w1", "stock_name": "AAPL"}]}
```

---

### `POST /chaos`
Kills the instance that handles this request. The system remains available via the other instances.

## Design decisions

- **Redis WATCH/MULTI/EXEC** — optimistic locking ensures buy/sell operations are atomic across multiple app instances without race conditions.
- **Three app instances** — satisfies the high-availability requirement: killing one still leaves two running.
- **`restart: always`** — Docker automatically restarts a killed instance, keeping the pool healthy long-term.
- **Nginx `proxy_next_upstream`** — if the upstream app dies mid-request (e.g. during `/chaos`), Nginx retries on the next healthy instance transparently to the client.
- **Redis as the single source of truth** — no in-memory state in the app, so horizontal scaling is trivial.
