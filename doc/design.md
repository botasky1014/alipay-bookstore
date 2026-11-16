# Alipay Bookstore Demo — Design Document

> Status: Approved (2026-11-16). Revised 2026-11-16: the return URL is now a dynamic setting that goes through the tunnel, like the notify URL (sections 2, 3.2, 6.4, 6.6, 12.7). This document describes the design only; it contains no implementation code.

## 1. Goals and Scope

An online bookstore demo for verifying Alipay sandbox "Computer Website Payment" (`alipay.trade.page.pay`). It covers: ordering, web payment / QR code payment, asynchronous notification, compensating queries, order closing, refund review and execution, and fund summary.

- Two fixed accounts: `buyer` (USER) and `admin` (ADMIN). No passwords, and backend endpoints have no authorization.
- 30 fixed English books. Books cannot be added or deleted. No pagination.
- The UI is entirely in English. Amounts are in CNY with two decimal places.

## 2. Architecture

```
                       ┌────────────── docker compose ───────────────┐
 Browser ─────:3000──▶ │ frontend (nginx)                             │
 Tunnel ──────:3000──▶ │   /            → Vue SPA static files          │
 (Alipay notify and    │   /images/     → book covers                   │
  return redirect)     │   /api/*       → proxy → backend:8000          │
                       │                                              │
                       │ backend (Spring Boot)  ──▶ postgres:5432      │
                       └──────────────────────────────────────────────┘
 backend ──HTTPS──▶ Alipay sandbox gateway
```

| Service | Image / Build | Port |
|---|---|---|
| `postgres` | Official PostgreSQL, latest stable, data persisted in a volume | Internal only (optionally expose 5432 for debugging) |
| `backend` | Multi-stage build: Gradle build → JRE runtime | 8000 (also exposed on the host for debugging) |
| `frontend` | Multi-stage build: Node builds Vue → served by nginx | 3000 (browser and tunnel target) |

The browser only talks to port 3000. nginx reverse-proxies all `/api` requests to the backend, so there are no CORS issues. The tunnel also points at port 3000, so one public address serves both Alipay callbacks: the asynchronous notification (`/api/alipay/notify`, proxied to the backend) and the browser return redirect (`/pay/return`, the SPA page).

### Tech Stack (latest stable versions at implementation time, pinned)

- Backend: Java (latest LTS), Spring Boot, Gradle (Groovy DSL), Spring Data JPA, Flyway, official `alipay-sdk-java`.
- Frontend: Vue 3, Vite, Vue Router, Pinia, Axios. No UI component library; simple hand-written styles (fewer dependencies).
- Database: PostgreSQL.
- Testing: JUnit 5 + Spring Boot Test + Testcontainers (PostgreSQL).

## 3. Configuration

### 3.1 Static Configuration (`.env`, injected by docker compose)

| Variable | Description |
|---|---|
| `ALIPAY_APP_ID` | Sandbox APPID |
| `ALIPAY_SELLER_ID` | Sandbox PID, used to verify `seller_id` in notifications |
| `ALIPAY_GATEWAY` | Sandbox gateway `https://openapi-sandbox.dl.alipaydev.com/gateway.do` |
| `ALIPAY_APP_PRIVATE_KEY` | App private key (PKCS8) |
| `ALIPAY_PUBLIC_KEY` | Alipay public key (for signature verification) |
| `ALIPAY_TIMEOUT_EXPRESS` | Alipay transaction timeout, default `2h` (see section 12) |
| `DB_*` | Database name, user, password |

Signing: RSA2, public key mode, charset UTF-8, format JSON.

**Key security**: Values from `info/info.txt` are copied only into `.env`; they are never written into code, documentation, or images. The repository provides `.env.example` (variable names only), and `.gitignore` includes `.env` and `info/`.

### 3.2 Dynamic `notify_url` and `return_url` Configuration (takes effect without restart)

Both URLs go through the tunnel, whose public address changes often, so neither is in `.env`.

- Table `system_settings(setting_key, setting_value, updated_at)`. Flyway seeds two records, `alipay.notify_url` and `alipay.return_url`, both initially empty. (The column names avoid the SQL keywords `KEY` and `VALUE`.)
- The admin **Settings** page views and edits both in one form. On save, each value must be a valid `http(s)://` URL. Each URL keeps its own `updated_at`, which changes only when that value changes.
- The backend `SettingsService` reads the latest values from the database every time a payment is initiated (primary-key lookups, no cache, so no consistency issues).
- After a change, every subsequent "pay / continue payment" uses the new URLs. If either is not configured, initiating payment fails with a message telling the admin to set them first.
- The tunnel targets host `localhost:3000` (nginx). Example values for a tunnel at `https://xxxx.example-tunnel.io`:
  - `notify_url`: `https://xxxx.example-tunnel.io/api/alipay/notify`
  - `return_url`: `https://xxxx.example-tunnel.io/pay/return`

## 4. Data Model

All amounts use `NUMERIC(10,2)`; all timestamps use `TIMESTAMPTZ`.

| Table | Main Columns | Notes |
|---|---|---|
| `users` | id, username (unique), role (`USER`/`ADMIN`) | Seeded with buyer and admin |
| `books` | id, title, author, description, cover_image, price, stock (≥0, CHECK) | 30 seeded books; only title/price/stock are editable |
| `cart_items` | id, user_id, book_id, quantity (>0), created_at; unique(user_id, book_id) | Cart, persisted on the server |
| `orders` | id, order_no (unique, used as `out_trade_no`), user_id, total_amount, status, alipay_trade_no, close_reason, pay_requested_at, paid_at, completed_at, closed_at, created_at, updated_at | |
| `order_items` | id, order_id, book_id, title_snapshot, unit_price_snapshot, quantity, subtotal | Price and title snapshots |
| `refund_requests` | id, order_id (unique), out_request_no (unique), reason, refund_amount, status, last_error, reviewed_at, created_at | At most one per order, so it cannot be re-requested |
| `fund_transactions` | id, type (`PAYMENT_IN`/`REFUND_OUT`), order_id, amount, alipay_trade_no, occurred_at; unique(order_id, type) | Fund flow; the unique constraint guarantees idempotency |
| `system_settings` | setting_key, setting_value, updated_at | Dynamic configuration (`alipay.notify_url`, `alipay.return_url`) |

`order_no` format: `yyyyMMddHHmmss` + 6 random digits, e.g. `20261116153012483920`.

## 5. State Machines

### 5.1 Order

| Status | Display | Final |
|---|---|---|
| `PENDING_PAYMENT` | Pending Payment | |
| `PAID` | Paid | |
| `REFUNDING` | Refunding | |
| `REFUNDED` | Refunded | ✔ |
| `CLOSED` | Closed | ✔ |
| `COMPLETED` | Completed | ✔ |

```
PENDING_PAYMENT ──payment succeeded (notify / return query / compensation job)──▶ PAID
PENDING_PAYMENT ──closed by user / Alipay returns TRADE_CLOSED──▶ CLOSED   (restore stock)
PAID ──user requests refund──▶ REFUNDING
PAID ──30 minutes after payment──▶ COMPLETED
REFUNDING ──admin approves and Alipay refund succeeds──▶ REFUNDED        (restore stock)
REFUNDING ──admin rejects──▶ COMPLETED
```

Every state transition is a conditional update: `UPDATE orders SET status=? WHERE id=? AND status=?`. Zero affected rows means a concurrent conflict or duplicate processing, which guarantees idempotency. For example, if "request refund" and "auto-complete" happen at the same time, only one succeeds.

### 5.2 Refund Request

`PENDING_REVIEW` (Pending Review) → `REFUNDED` (Refunded, final) / `REJECTED` (Rejected, final). If the refund call fails, the request stays `PENDING_REVIEW`, `last_error` is recorded, and it can be retried.

## 6. Core Flows

### 6.1 Login

`POST /api/auth/login {username}` → look up the `users` table. If found, return `{id, username, role}`; otherwise 404 "Username does not exist". The frontend stores the result in localStorage and redirects by role: USER → `/`, ADMIN → `/admin/inventory`. Logout clears localStorage. Later requests are not authenticated; the frontend sends `userId` where needed.

### 6.2 Cart

- Add to cart: quantity ≥ 1. If the sum with the existing quantity exceeds stock, it is capped at the stock limit.
- Opening the cart (`GET /api/cart`) syncs it once: quantities above current stock are reduced to the stock limit, and items with zero stock are deleted. Prices are shown in real time.
- The side panel supports changing quantity, deleting, and selecting items; selected items are paid for via **Checkout**.

### 6.3 Placing an Order (stock is deducted at order time)

Two entry points: **Buy Now** on the detail page (one book) and **Checkout** in the cart (multiple selected books). Both call `POST /api/orders` with body `{userId, items:[{bookId, quantity}], fromCart}`.

Within a single database transaction:

1. Lock the involved books with `SELECT ... FOR UPDATE` in ascending book_id order (fixed lock order prevents deadlocks).
2. Check that each book's stock ≥ the requested quantity; otherwise fail the whole order and return the titles that are short.
3. Deduct stock; create `order_items` snapshots with the current price and title; calculate the total.
4. Create the order as `PENDING_PAYMENT`.
5. If `fromCart`, delete the corresponding cart records.

After the order is created, the frontend proceeds to payment method selection.

### 6.4 Initiate / Continue Payment

`POST /api/orders/{id}/pay {method: WEB | QR}`

1. The order must be `PENDING_PAYMENT`.
2. If payment was initiated before (`pay_requested_at` is not null), first call `alipay.trade.query` to sync status:
   - `TRADE_SUCCESS` / `TRADE_FINISHED` → mark as paid and return "already paid";
   - `TRADE_CLOSED` → close the order, restore stock, and show "Payment expired, order closed";
   - `ACQ.TRADE_NOT_EXIST` / `WAIT_BUYER_PAY` → continue to step 3;
   - Network errors or other uncertain results → return an error without changing state.
3. Read the latest `notify_url` and `return_url` from the settings; if either is empty, fail with "not configured". Call `alipay.trade.page.pay` with the original order number as `out_trade_no`, the snapshot total as `total_amount`, both URLs, and `timeout_express`:
   - `WEB`: standard cashier page (buyer can log in to their account to pay);
   - `QR`: `qr_pay_mode=2`, redirects to the Alipay QR code page.
4. Record `pay_requested_at` and return the auto-submit form HTML generated by the SDK. The frontend inserts and submits it, and the browser goes to Alipay.

### 6.5 Asynchronous Notification `POST /api/alipay/notify`

1. Verify the signature with the Alipay public key using `rsaCheckV1`; on failure return `failure`.
2. Check `app_id`, `seller_id`, that `out_trade_no` exists, and that `total_amount` matches the order; on mismatch log it and return `failure`.
3. If `trade_status` is `TRADE_SUCCESS` / `TRADE_FINISHED`: conditionally update the order from `PENDING_PAYMENT` to `PAID`, write `alipay_trade_no` and `paid_at = gmt_payment`, and insert a `PAYMENT_IN` fund transaction (the unique constraint prevents double-counting on duplicate notifications).
4. If the order is already in another status (duplicate notification): return `success` directly.
5. On success, return plain text `success`.

Edge case: the order is already `CLOSED` locally but a payment-success notification arrives (very unlikely, since closing queries Alipay first). Handling: record `PAYMENT_IN`, then automatically issue a full refund and record `REFUND_OUT`. The order stays `CLOSED`, and a warning is logged.

### 6.6 Synchronous Return `return_url`

- `return_url` is the admin-configured tunnel address of the frontend page `/pay/return` (for example `https://xxxx.example-tunnel.io/pay/return`).
- The page works without a logged-in session, because the browser may come back on the tunnel address, which is a different origin from `localhost:3000` and has no stored login. Its result does not depend on the login: the backend checks the signature and queries Alipay.
- The page sends all URL parameters to `POST /api/alipay/return-check`. The backend verifies the signature, then **uses the result of `alipay.trade.query` as the source of truth** to sync the order status, and returns it.
- Frontend display: paid → **Payment Successful** page (order number, amount, items); still pending → "Processing", polling the order status every 2 seconds for up to 30 seconds, then advising the user to check the orders page later.

### 6.7 Scheduled Jobs

| Job | Frequency | Logic |
|---|---|---|
| Payment compensation | Every 60 s | Scan `PENDING_PAYMENT` orders with non-null `pay_requested_at` and call `trade.query` for each: success → mark paid; `TRADE_CLOSED` → close and restore stock; anything else → no action |
| Auto-complete | Every 60 s | Conditionally update `PAID` orders with `paid_at <= now() - 30min` to `COMPLETED` |

Implemented with Spring `@Scheduled`. Single-instance deployment, so no distributed lock is needed.

### 6.8 User Closes an Order

`POST /api/orders/{id}/close`; the order must be `PENDING_PAYMENT`:

- If payment was initiated before, first call `trade.query`:
  - Paid → mark `PAID`, reject the close, and inform the user;
  - `WAIT_BUYER_PAY` → call `alipay.trade.close`, and close locally only after it succeeds;
  - `TRADE_NOT_EXIST` / `TRADE_CLOSED` → close directly;
  - Uncertain query result → return an error and do not close.
- Closing restores stock in the same transaction (`stock = stock + quantity`).

### 6.9 Refund

**Request**: `POST /api/orders/{id}/refund-requests {reason}`; reason is required. The order is conditionally updated `PAID → REFUNDING`, and a `PENDING_REVIEW` refund request is created. `out_request_no` is generated at creation and fixed (idempotent retries). Refund amount = order total. An order that already has a refund request cannot request again.

**Approve**: `POST /api/admin/refund-requests/{id}/approve`

1. The request must be `PENDING_REVIEW`.
2. Call `alipay.trade.refund` (`out_trade_no`, `refund_amount`, `out_request_no`).
3. Success criterion: response code `10000`. If a retry returns an ambiguous result, confirm with `alipay.trade.fastpay.refund.query`.
4. On success, in one transaction: request → `REFUNDED`, order `REFUNDING → REFUNDED`, restore stock, insert a `REFUND_OUT` fund transaction.
5. On failure or timeout: the request stays `PENDING_REVIEW`, `last_error` is written, the endpoint returns the error message, and the admin can click again to retry.

**Reject**: `POST /api/admin/refund-requests/{id}/reject` → request `REJECTED`, order `REFUNDING → COMPLETED`.

### 6.10 Admin: Inventory Management

- The list is the same as the user home page (cover, price, stock, title).
- The detail page allows editing:
  - **Stock**: enter a delta (e.g. `+10`, `-5`). The backend runs `UPDATE books SET stock = stock + :delta WHERE id = :id AND stock + :delta >= 0`. Using a delta instead of an absolute value avoids overwriting concurrent order deductions. A result below 0 returns an error. Because stock is deducted at order time, the displayed stock already excludes quantities held by pending orders, so "not below 0" also satisfies "not below the quantity held by pending orders".
  - **Price**: must be > 0; affects only new orders (existing orders use snapshots).
  - **Title**: non-empty; likewise affects only new order snapshots. Existing orders display the snapshot title.

### 6.11 Admin: Fund Management

- Local net total = Σ`PAYMENT_IN` − Σ`REFUND_OUT`.
- Transaction list: time, type (Payment In / Refund Out), order number, amount, Alipay trade number, newest first, no pagination.
- Alipay balance: call `alipay.data.bill.balance.query`. The page shows **Local Net Total**, **Alipay Balance**, and **Difference**, with a note that the comparison is for reference only (the sandbox account has a pre-existing balance, so they will not match). If the call fails or the sandbox does not support it, show `Unavailable`.

## 7. API Overview

| Method | Path | Description |
|---|---|---|
| POST | `/api/auth/login` | Login |
| GET | `/api/books` / `/api/books/{id}` | Book list / detail |
| GET | `/api/cart?userId=` | Cart (with stock sync) |
| POST | `/api/cart/items` | Add to cart |
| PUT / DELETE | `/api/cart/items/{id}` | Update quantity / delete |
| POST | `/api/orders` | Place order |
| GET | `/api/orders?userId=` / `/api/orders/{id}` | Order list / detail |
| POST | `/api/orders/{id}/pay` | Initiate or continue payment |
| POST | `/api/orders/{id}/close` | Close order |
| POST | `/api/orders/{id}/refund-requests` | Request refund |
| POST | `/api/alipay/notify` | Alipay async notification (form-encoded, returns plain text) |
| POST | `/api/alipay/return-check` | Return-URL signature verification and status sync |
| PATCH | `/api/admin/books/{id}` | Edit title, price, stock delta |
| GET | `/api/admin/refund-requests` | All refund requests |
| POST | `/api/admin/refund-requests/{id}/approve` / `reject` | Approve / reject |
| GET | `/api/admin/funds` | Fund summary, transactions, Alipay balance comparison |
| GET / PUT | `/api/admin/settings/alipay-urls` | View / update `notifyUrl` and `returnUrl` together |

Errors are returned uniformly as `{code, message}`, with HTTP status codes 400 (invalid parameters), 404 (not found), 409 (state conflict, insufficient stock), and 502 (Alipay call failed).

## 8. Frontend Pages

| Route | Page |
|---|---|
| `/login` | Enter username to log in |
| `/` | User home: grid of 30 books |
| `/books/:id` | Book detail: quantity selector (1..stock), Add to Cart, Buy Now |
| `/orders` | My Orders: all orders with items; pending → Pay (choose Web / QR), Close; paid → Request Refund (dialog for reason) |
| `/pay/return` | Payment return handling and Payment Successful page (no login required) |
| Side panel | Cart: slides in from the right; change quantity, delete, select, Checkout |
| `/admin/inventory` | Inventory home: grid of 30 books |
| `/admin/books/:id` | Edit stock delta, price, title |
| `/admin/refunds` | Refund request list: Approve / Reject, with error messages |
| `/admin/funds` | Fund management |
| `/admin/settings` | Alipay notify URL and return URL settings |

Top navigation:
- User: `buyer` · Home · My Orders · Cart · Logout
- Admin: `admin` · Inventory · Refund Requests · Funds · Settings · Logout

Frontend route guards only handle navigation convenience (redirect to `/login` when not logged in and back to the requested page after login, show navigation by role); they are not access control. `/pay/return` is reachable with or without a login.

## 9. Book Data

- Select 30 well-known English books from Open Library (title, author, description). Covers are downloaded to `frontend/public/images/books/{id}.jpg` and served by nginx.
- Data is written into a Flyway migration (`V2__seed_books.sql`), with fixed prices between ¥9.90 and ¥499.00 and initial stock of 50.
- Covers are downloaded once during development and committed to the repository; nothing is fetched from the internet at runtime.

## 10. Project Structure

```
alipay-demo/
├── docker-compose.yml
├── .env.example
├── README.md
├── doc/design.md
├── backend/
│   ├── build.gradle / settings.gradle / Dockerfile
│   └── src/
│       ├── main/java/.../
│       │   ├── auth/  book/  cart/  order/  payment/  refund/  fund/  settings/
│       │   ├── alipay/        # AlipayGateway interface + SDK implementation, notify/return controllers
│       │   ├── scheduler/     # payment compensation, auto-complete
│       │   └── common/        # exception handling, error responses
│       ├── main/resources/db/migration/   # Flyway scripts
│       └── test/java/...                  # integration tests
└── frontend/
    ├── package.json / vite.config.js / Dockerfile / nginx.conf
    ├── public/images/books/
    └── src/ (views/, components/, stores/, api/, router/)
```

All Alipay calls go through the `AlipayGateway` interface (pagePay, query, close, refund, refundQuery, balanceQuery, verifySignature). Business code does not depend on the SDK directly, so it can be replaced in tests.

## 11. Testing

Integration tests only: Spring Boot Test + Testcontainers with a real PostgreSQL; `AlipayGateway` is replaced by a mock and the sandbox is not called. Coverage:

- Ordering deducts stock, insufficient stock, cart items removed after checkout;
- Cart quantity capping and deletion when stock is 0;
- Notify signature failure, amount mismatch, idempotent duplicate notifications;
- return-check uses the query result as the source of truth;
- Continue payment hitting `TRADE_CLOSED` auto-closes the order and restores stock;
- Close is rejected when the pre-close query shows the order is paid;
- Compensation job and 30-minute auto-complete (time controlled via an injectable `Clock`);
- Refund request (PAID only, reason required, no re-request), approve success, approve failure with retry, reject;
- Admin stock delta cannot go below 0; price changes do not affect existing orders;
- After notify_url and return_url are changed, the next payment uses the new values immediately; payment is rejected while either is empty.

Running the tests requires Docker on the local machine.

## 12. Additional Design Decisions (confirmed)

The following decisions go beyond the original requirements and have all been confirmed:

1. **`timeout_express` defaults to `2h`**: an Alipay transaction unpaid after this time is closed by Alipay, and the system then closes the order accordingly. Adjustable via `.env`.
2. **Stock is edited by delta** rather than by entering a new value (prevents concurrent overwrites).
3. **Title changes affect only new orders**; existing orders display the title snapshot from order time.
4. **Automatic refund when a payment-success notification arrives for an order already closed locally** (edge case in 6.5).
5. **A Settings entry is added to the admin navigation** (not in the original requirement's navigation).
6. **Balance comparison uses `alipay.data.bill.balance.query`**; if the sandbox does not support it, show Unavailable.
7. **The return URL is a dynamic setting that goes through the tunnel** (changed 2026-11-16). This replaces the earlier decision that `return_url` was fixed to `http://localhost:3000/pay/return` in `.env` and did not need the tunnel. Both URLs are now edited on the Settings page, the tunnel points at port 3000, and `/pay/return` no longer requires a login.
