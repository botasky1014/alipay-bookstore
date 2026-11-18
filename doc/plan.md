# Implementation Plan

> Status: Approved (2026-11-16). Phase 18 added 2026-11-16 for the change requests after the first sandbox test.
> Source of truth for behavior: [design.md](design.md). Section numbers below (for example "D6.4") refer to that document.
> Each step is marked `[x]` when it is finished and verified.

## Starting State (before Phase 1)

The project skeleton was in place and runs under docker compose:

- `backend/`: Spring Boot 4.1.2, Gradle 9.9.1 (Groovy), Java 25 bytecode. Dependencies for web, JPA, validation, Flyway, PostgreSQL and the Alipay SDK are declared. Only `GET /api/health` exists. Flyway has a placeholder `V1__init.sql`. `application.yml` already reads the database and `alipay.*` settings from environment variables.
- `backend/src/test/.../ApplicationIT.java`: one Testcontainers integration test (PostgreSQL 18).
- `frontend/`: Vue 3, Vite, Vue Router, Pinia, Axios. One placeholder `HomeView` that calls `/api/health`. nginx serves the SPA and proxies `/api` to the backend.
- Root: `docker-compose.yml`, `.env` / `.env.example`, `.dockerignore` (allow-list), `certs/` for an optional extra CA.

## Conventions for Every Step

- Backend packages follow D10: `auth`, `book`, `cart`, `order`, `payment`, `refund`, `fund`, `settings`, `alipay`, `scheduler`, `common`, under `com.example.bookstore`.
- Every order state change is a conditional update (`WHERE id = ? AND status = ?`), as in D5.1.
- Integration tests are written in the same step as the feature they cover (D11). `AlipayGateway` is mocked in tests; the sandbox is never called from tests.
- A step is done only when `./gradlew test` passes and, for steps with UI, the page works in `docker compose up -d --build`.
- All code, comments, UI text and docs are in English.

---

## Phase 0: Skeleton

- [x] **0.1** Runnable empty project: backend, frontend, PostgreSQL, docker compose, `.env`, README, one passing integration test.

## Phase 1: Database and Seed Data

`V1__init.sql` has already been applied to the existing database volume, so it stays unchanged; new migrations start at V2. (D9 names the book seed `V2__seed_books.sql`; it becomes `V4` because schema and base seed come first.)

- [x] **1.1 Schema migration** `V2__schema.sql`: tables `users`, `books`, `cart_items`, `orders`, `order_items`, `refund_requests`, `fund_transactions`, `system_settings` with the columns, `NUMERIC(10,2)` amounts, `TIMESTAMPTZ` timestamps, CHECK constraints (`stock >= 0`, `quantity > 0`) and unique constraints from D4. Add indexes for `orders(user_id)`, `orders(status)`.
- [x] **1.2 Base seed** `V3__seed_users_settings.sql`: users `buyer` (USER) and `admin` (ADMIN); `system_settings` row `alipay.notify_url` with an empty value.
- [x] **1.3 Book data and covers**: pick 30 well-known English books from Open Library (title, author, short description). Download covers once to `frontend/public/images/books/{id}.jpg`. Write `V4__seed_books.sql` with fixed prices between 9.90 and 499.00 CNY and stock 50 (D9).
- Verify: migrations apply cleanly on an empty database; `ApplicationIT` still passes.

## Phase 2: Backend Foundation

- [x] **2.1 JPA entities and repositories** for all tables, with enums `Role`, `OrderStatus`, `RefundStatus`, `FundType`. Hibernate `ddl-auto: validate` must pass against the Flyway schema.
- [x] **2.2 Error handling** (`common`): `ApiException` with HTTP status and code; `@RestControllerAdvice` returning `{code, message}` for 400 / 404 / 409 / 502 and for bean-validation errors (D7).
- [x] **2.3 Clock bean**: a `Clock` bean (system zone `Asia/Shanghai`) used everywhere instead of `now()`, so tests can control time.
- [x] **2.4 Test support**: a shared abstract integration-test base with the PostgreSQL container, a `@MockitoBean AlipayGateway`, a mutable test `Clock`, and a helper to reset data between tests.

## Phase 3: Login and Books

- [x] **3.1 Login** `POST /api/auth/login` (D6.1): returns `{id, username, role}` or 404 "Username does not exist".
- [x] **3.2 Books** `GET /api/books`, `GET /api/books/{id}`: id, title, author, description, cover image URL, price, stock.
- [x] **3.3 Tests**: login success / unknown user; list returns 30 books.

## Phase 4: Cart

- [x] **4.1 Cart API** (D6.2): `GET /api/cart?userId=`, `POST /api/cart/items`, `PUT /api/cart/items/{id}`, `DELETE /api/cart/items/{id}`.
  - Adding merges with an existing row and caps at stock.
  - `GET` syncs first: caps quantities above stock, deletes rows whose book has zero stock; returns live prices and line totals.
- [x] **4.2 Tests**: merge and cap on add; cap and delete on read; update and delete.

## Phase 5: Orders

- [x] **5.1 Place order** `POST /api/orders` (D6.3): single transaction, lock books in ascending id with `SELECT ... FOR UPDATE`, check stock, deduct, write price and title snapshots, create `PENDING_PAYMENT` order with an `order_no` of `yyyyMMddHHmmss` + 6 random digits, delete cart rows when `fromCart`.
- [x] **5.2 Query orders** `GET /api/orders?userId=` (newest first, with items, status, refund request status and reason if any) and `GET /api/orders/{id}`.
- [x] **5.3 Stock restore helper** shared by close and refund: add each item's quantity back to its book.
- [x] **5.4 Tests**: stock deducted; insufficient stock fails the whole order and names the books; cart rows removed only for checked-out items; snapshots unaffected by later price changes.

## Phase 6: Settings (dynamic notify_url; return_url added in Phase 18)

- [x] **6.1 Settings API** `GET` / `PUT /api/admin/settings/notify-url` (replaced by `/api/admin/settings/alipay-urls` in 18.3) (D3.2): validates an `http(s)://` URL; `SettingsService.getNotifyUrl()` reads the database on every call, no cache.
- [x] **6.2 Tests**: invalid URL rejected; updated value is returned immediately.

## Phase 7: Alipay Gateway

- [x] **7.1 Configuration**: `@ConfigurationProperties("alipay")` record bound from the existing `alipay.*` keys; one `AlipayClient` bean (RSA2, UTF-8, JSON, public key mode).
- [x] **7.2 `AlipayGateway` interface** (D10) with small result types: `pagePay`, `query`, `close`, `refund`, `refundQuery`, `balanceQuery`, `verifySignature`. Query results map to a clear enum: `SUCCESS`, `WAIT_BUYER_PAY`, `CLOSED`, `NOT_EXIST`, `UNKNOWN`.
- [x] **7.3 SDK implementation** `SdkAlipayGateway` wrapping `alipay.trade.page.pay`, `alipay.trade.query`, `alipay.trade.close`, `alipay.trade.refund`, `alipay.trade.fastpay.refund.query`, `alipay.data.bill.balance.query` and `AlipaySignature.rsaCheckV1`. Network errors map to `UNKNOWN` / exceptions, never to a definite state.
- [x] **7.4 Manual sandbox smoke check** (not an automated test): call `query` for a non-existent order number against the sandbox and confirm `NOT_EXIST`, which proves keys, gateway and the CA setup work.

## Phase 8: Payment

- [x] **8.1 Payment sync service**: one place that applies a query or notification result to an order: mark paid (conditional update, `alipay_trade_no`, `paid_at`, insert `PAYMENT_IN` idempotently), or close and restore stock on `CLOSED`. Used by 8.2, 8.3, 8.4, 9.1 and 10.1.
- [x] **8.2 Initiate / continue payment** `POST /api/orders/{id}/pay` (D6.4): pre-query when `pay_requested_at` is set; then `page.pay` with the latest `notify_url`, the `return_url` (dynamic since 18.3), `timeout_express`, record `pay_requested_at`; return the form HTML. Fail with a clear message when `notify_url` is not configured.
- [x] **8.3 Async notification** `POST /api/alipay/notify` (D6.5): verify signature, check `app_id`, `seller_id`, order existence and amount, apply `TRADE_SUCCESS` / `TRADE_FINISHED`; plain-text `success` / `failure`. Edge case: paid notification for a locally `CLOSED` order records `PAYMENT_IN`, then refunds automatically and records `REFUND_OUT`.
- [x] **8.4 Return check** `POST /api/alipay/return-check` (D6.6): verify signature, then trust `trade.query` and sync; return the order status.
- [x] **8.5 Tests**: notify bad signature, amount mismatch, duplicate notification (single `PAYMENT_IN`); return-check uses query result; continue payment on `CLOSED` closes the order and restores stock; new `notify_url` used on the next payment; missing `notify_url` rejected.

## Phase 9: Close Order

- [x] **9.1 Close** `POST /api/orders/{id}/close` (D6.8): pre-query when payment was initiated; paid → mark paid and reject; `WAIT_BUYER_PAY` → `trade.close` then close; `NOT_EXIST` / `CLOSED` → close; unknown → error. Restore stock in the same transaction.
- [x] **9.2 Tests**: close never-paid order; close rejected when query shows paid; unknown query result leaves the order unchanged.

## Phase 10: Scheduled Jobs

- [x] **10.1 Payment compensation job** (D6.7): every 60 s, query `PENDING_PAYMENT` orders with `pay_requested_at` set and apply results through 8.1.
- [x] **10.2 Auto-complete job**: every 60 s, `PAID` orders with `paid_at <= now - 30 min` → `COMPLETED` (conditional update).
- [x] **10.3** Enable scheduling, but disable it in tests; tests call the job methods directly with the test `Clock`.
- [x] **10.4 Tests**: compensation marks paid / closes; auto-complete respects the 30-minute boundary and skips `REFUNDING`.

## Phase 11: Refunds

- [x] **11.1 Refund request** `POST /api/orders/{id}/refund-requests {reason}` (D6.9): reason required; `PAID → REFUNDING`; create `PENDING_REVIEW` request with a fixed `out_request_no`; reject a second request.
- [x] **11.2 Admin list** `GET /api/admin/refund-requests`: all requests, newest first, with order number, amount, reason, status, last error.
- [x] **11.3 Approve** `POST /api/admin/refund-requests/{id}/approve`: call `trade.refund`; confirm with `refundQuery` if ambiguous; on success, in one transaction mark request and order `REFUNDED`, restore stock, insert `REFUND_OUT`; on failure keep `PENDING_REVIEW`, store `last_error`, return 502.
- [x] **11.4 Reject** `POST /api/admin/refund-requests/{id}/reject`: request `REJECTED`, order `REFUNDING → COMPLETED`.
- [x] **11.5 Tests**: only `PAID` can request; reason required; no second request; approve success restores stock and records fund flow; approve failure then retry succeeds with the same `out_request_no`; reject completes the order.

## Phase 12: Admin Inventory and Funds

- [x] **12.1 Edit book** `PATCH /api/admin/books/{id}` (D6.10): optional `stockDelta` (atomic `stock + delta >= 0`), `price` (> 0), `title` (non-empty).
- [x] **12.2 Funds** `GET /api/admin/funds` (D6.11): local net total, transaction list (newest first), Alipay balance and difference, or `Unavailable` when the balance call fails.
- [x] **12.3 Tests**: delta below zero rejected; price change does not affect existing order snapshots; fund totals after pay and refund; balance failure returns `Unavailable`.

## Phase 13: Frontend Foundation

- [x] **13.1 Session store** (Pinia): current user saved to and restored from localStorage; logout clears it.
- [x] **13.2 Router and guards**: all routes from D8; redirect to `/login` when not logged in; redirect by role after login.
- [x] **13.3 Layouts**: user header (`buyer` · Home · My Orders · Cart · Logout) and admin header (`admin` · Inventory · Refund Requests · Funds · Settings · Logout).
- [x] **13.4 Shared pieces**: API modules per area, error message display from `{code, message}`, book card grid component reused by user home and admin inventory, money and date formatting, status badges.

## Phase 14: Frontend User Pages

- [x] **14.1 Login page**.
- [x] **14.2 Home**: grid of 30 books (cover, price, stock, title), click to detail.
- [x] **14.3 Book detail**: quantity selector limited to stock, Add to Cart, Buy Now (creates order, then redirects to Alipay web payment).
- [x] **14.4 Cart side panel**: slides in from the right; change quantity, delete, select, Checkout.
- [x] **14.5 Payment redirect**: Buy Now, Checkout and Pay submit the returned Alipay web payment form directly (no method dialog).
- [x] **14.6 My Orders**: all orders with items and status; Pay and Close for pending; Request Refund dialog (reason required) for paid; refund status shown.
- [ ] **14.7 Payment return page** `/pay/return`: calls return-check; shows Payment Successful, or Processing with polling every 2 s for up to 30 s.

## Phase 15: Frontend Admin Pages

- [x] **15.1 Inventory**: same grid; click to edit page.
- [x] **15.2 Book edit**: stock delta, price, title, with validation messages.
- [x] **15.3 Refund Requests**: list with Approve / Reject; shows `last_error` and allows retry.
- [x] **15.4 Funds**: Local Net Total, Alipay Balance, Difference (with reference-only note), transaction list.
- [x] **15.5 Settings**: view and update `notify_url` (and `return_url` since 18.5).

## Phase 16: End-to-End Verification with the Sandbox

Manual run with `docker compose up -d --build` and a tunnel pointing to host port 3000 (nginx), which serves both the
notify path `/api/alipay/notify` and the return page `/pay/return`.

Already verified against the sandbox without a tunnel: signed page-pay form accepted by the Alipay
cashier, server-side `trade.query` (signature and TLS from the container), continue payment after
`ACQ.TRADE_NOT_EXIST`, close, `balance.query`, and the refund error path (`ACQ.TRADE_NOT_EXIST`
recorded and retryable). The open steps below need a running tunnel and a real sandbox buyer
payment, so they are done by hand; 14.7 is completed together with 16.2.

- [ ] **16.1** Set `notify_url` and `return_url` in Settings to the tunnel addresses; confirm they are used without restarting.
- [ ] **16.2** Web payment: buy now → pay with a sandbox buyer account → return page shows success → order `PAID`, stock deducted, `PAYMENT_IN` recorded.
- [ ] **16.4** Notification lost: stop the tunnel, pay, confirm the compensation job marks the order paid.
- [x] **16.5** Continue payment and close on a pending order.
- [ ] **16.6** Refund: request → admin approves → order `REFUNDED`, stock restored, `REFUND_OUT` recorded; and a reject path.
- [ ] **16.7** Auto-complete after 30 minutes.
- [x] **16.8** Funds page: local total and Alipay balance comparison (or `Unavailable`).
- [ ] **16.9** Change both URLs to a new tunnel address and confirm the next payment's notification and return redirect arrive at the new address.

## Phase 17: Finish

- [x] **17.1** README: features, configuration, how to set the Alipay URLs, how to run tests; no sandbox account instructions.
- [x] **17.2** Final pass: all tests green, `docker compose up -d --build` from a clean volume works, no non-English text in the repository.

## Phase 18: Changes After the First Sandbox Test

- [x] **18.1 Return page without login**: after a QR payment the browser landed on `/login`, because `/pay/return`
  required a stored login and the redirect dropped Alipay's parameters. `/pay/return` is now reachable without a
  login, and other pages send the user back to the requested page after logging in (D6.6, D8).
- [x] **18.2 Migration** `V5__seed_return_url_setting.sql`: `system_settings` row `alipay.return_url` with an empty value.
- [x] **18.3 Dynamic return URL (backend)** (D3.2, D12.7): `SettingsService` stores both URLs; `GET` / `PUT
  /api/admin/settings/alipay-urls` replaces the notify-url endpoint; pay / continue payment reads both URLs on every
  call and fails with `ALIPAY_URLS_NOT_SET` while either is empty. `alipay.return-url` / `ALIPAY_RETURN_URL` removed
  from `application.yml`, `docker-compose.yml` and `.env.example`.
- [x] **18.4 Tests**: settings default empty, both URLs updated together, per-URL `updatedAt`, invalid URLs rejected
  for either field; the next payment uses both new URLs; payment rejected while either URL is empty.
- [x] **18.5 Settings page**: one form for both URLs with the expected paths and a note that the tunnel targets port 3000.
- [x] **18.6 Docs**: design.md, plan.md and README updated.
- [x] **18.7 Verify**: 56 tests green; V5 applied on the existing database; saving both URLs on the Settings page
  puts them into the next payment form; a notification posted through nginx on port 3000 reaches the backend.
