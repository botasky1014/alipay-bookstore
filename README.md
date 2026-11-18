# alipay-demo

An online bookstore demo for verifying Alipay sandbox **Computer Website Payment**
(`alipay.trade.page.pay`): web payment, asynchronous notifications, compensating
queries, order closing, refund review and fund reconciliation.

- Design: [doc/design.md](doc/design.md)
- Implementation plan and progress: [doc/plan.md](doc/plan.md)

## Features

**Buyer** (log in as `buyer`)

- Browse 30 books, view details, add to the cart or buy now (quantity limited by stock).
- Server-side cart in a side panel: change quantities, remove, select items and check out.
  The cart is kept after logout.
- Buy Now or Checkout redirects straight to the Alipay web payment page; return to a payment result page.
- My Orders: continue paying or close pending orders, request a full refund (with a reason) for paid orders.

**Administrator** (log in as `admin`)

- Inventory: change stock by a delta, price and title of each book (changes affect new orders only).
- Refund Requests: approve (refunds through Alipay, restores stock) or reject (completes the order).
  Failed refunds stay pending with the error shown and can be retried.
- Funds: local net total (payments in minus refunds out), every fund transaction, and the Alipay
  balance for reference.
- Settings: the Alipay notify URL and return URL, changeable at any time without restarting.

**Order status**: Pending Payment, Paid, Refunding, Refunded, Closed, Completed.
Paid orders complete automatically 30 minutes after payment. A background job queries Alipay every
minute for pending orders, so payments are picked up even when a notification is lost.

## Stack

| Part | Technology |
|---|---|
| Frontend | Vue 3, Vite, Vue Router, Pinia, Axios, served by nginx |
| Backend | Java 25, Spring Boot 4, Gradle (Groovy DSL), Spring Data JPA, Flyway, official Alipay SDK |
| Database | PostgreSQL 18 |
| Runtime | Docker Compose |

| Service | Port |
|---|---|
| Frontend (nginx) | 3000 |
| Backend (Spring Boot) | 8000 |

nginx proxies `/api/*` to the backend, so the browser only needs port 3000. The tunnel for Alipay
also points at port 3000, so one public address serves both the notification endpoint and the
payment return page. Port 8000 is exposed on the host only for debugging.

## Getting Started

1. Create the environment file and fill in the Alipay sandbox values
   (APPID, PID, app private key, Alipay public key; public key mode, RSA2):

   ```bash
   cp .env.example .env
   ```

2. Build and start everything:

   ```bash
   docker compose up -d --build
   ```

3. Open http://localhost:3000 and log in as `buyer` or `admin`.

Stop with `docker compose down`. Add `-v` to also delete the database volume.

## Configuring the Alipay URLs

Alipay calls back to two addresses, and both go through a tunnel:

- **Notify URL**: Alipay's server posts the payment result here (`/api/alipay/notify`).
- **Return URL**: Alipay sends the buyer's browser here after paying (`/pay/return`).

1. Start a tunnel (any tunneling tool) that forwards a public HTTPS address to `localhost:3000`.
2. Log in as `admin`, open **Settings**, and enter:
   - Notify URL: `https://<tunnel-host>/api/alipay/notify`
   - Return URL: `https://<tunnel-host>/pay/return`
3. Save. The next payment uses the new URLs; nothing needs to be restarted.

Buyers cannot start a payment until both URLs are set. When the tunnel address changes, update
both again in Settings. If a notification is lost while the tunnel is down, the background query
job still marks the order as paid within about a minute.

The return page works without a login, because the browser comes back on the tunnel address, which
is a different site from `localhost:3000` and has no stored login. Links from that page to other
pages ask the buyer to log in on that address.

## Configuration Reference

| Variable | Description |
|---|---|
| `DB_NAME`, `DB_USER`, `DB_PASSWORD` | PostgreSQL database and credentials |
| `ALIPAY_APP_ID` | Sandbox APPID |
| `ALIPAY_SELLER_ID` | Sandbox PID; notifications from other sellers are rejected |
| `ALIPAY_GATEWAY` | Sandbox gateway URL |
| `ALIPAY_APP_PRIVATE_KEY` | App private key (PKCS8, single line) |
| `ALIPAY_PUBLIC_KEY` | Alipay public key, used to verify signatures |
| `ALIPAY_TIMEOUT_EXPRESS` | How long an unpaid Alipay transaction stays open (default `2h`); after that the order is closed |

The notify URL and return URL are not environment variables; they are set on the Settings page.
An `ALIPAY_RETURN_URL` line left over in an older `.env` is ignored and can be deleted.

### Networks with TLS inspection

If your network inspects HTTPS traffic (for example a corporate proxy), put the proxy's root CA
certificate (PEM, `*.crt`) in `certs/`. The Docker builds import it so that Gradle, npm and the
backend's calls to the Alipay gateway trust it. See [certs/README.md](certs/README.md).

For local Gradle runs on such a network, point the JDK at a truststore that contains the CA,
for example via `JAVA_TOOL_OPTIONS="-Djavax.net.ssl.trustStore=<path> -Djavax.net.ssl.trustStorePassword=<password>"`.

## Local Development

Backend (requires JDK 25 or newer and a running PostgreSQL):

```bash
cd backend && ./gradlew bootRun
```

Frontend (dev server on port 3000, proxies `/api` to port 8000):

```bash
cd frontend && npm install && npm run dev
```

## Tests

Integration tests use Testcontainers (a real PostgreSQL) and a mocked Alipay gateway, so they never
call the sandbox. Docker must be running:

```bash
cd backend && ./gradlew test
```

## Project Layout

```
backend/    Spring Boot application
  src/main/java/com/example/bookstore/
    auth/ book/ cart/ order/ payment/ refund/ fund/ settings/   features
    alipay/      AlipayGateway interface and SDK implementation
    scheduler/   payment compensation and auto-complete jobs
    common/      error handling, clock, scheduling config
  src/main/resources/db/migration/   Flyway migrations and seed data
frontend/   Vue single-page application and nginx config
  public/images/books/   book covers (from Open Library)
certs/      optional extra CA certificates for Docker builds
doc/        design and plan
```
