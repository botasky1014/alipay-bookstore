# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project

Online bookstore demo for verifying the Alipay sandbox "Computer Website Payment" (`alipay.trade.page.pay`): web payment, async notify, compensating queries, order close, refund review, fund reconciliation. Vue 3 SPA (nginx) + Spring Boot 4 / Java 25 backend + PostgreSQL 18, all via Docker Compose.

`doc/design.md` is the source of truth for behavior (the plan cites its sections as "D6.4" etc.). `doc/plan.md` tracks implementation progress; mark steps `[x]` when finished and verified. All code, comments, UI text and docs are in English.

## Commands

```bash
docker compose up -d --build          # full stack; UI at http://localhost:3000 (log in as buyer or admin)
docker compose down [-v]              # -v also drops the DB volume

cd backend && ./gradlew bootRun       # needs JDK >= 25 and a local PostgreSQL (defaults: localhost:5432, bookstore/bookstore)
cd backend && ./gradlew test          # integration tests; Docker must be running (Testcontainers)
cd backend && ./gradlew test --tests OrderIT                  # single test class
cd backend && ./gradlew test --tests 'OrderIT.someMethod'     # single test method

cd frontend && npm install && npm run dev    # Vite on :3000, proxies /api to :8000
cd frontend && npm run build
```

A step is considered done only when `./gradlew test` passes and, for UI changes, the page works under `docker compose up -d --build`.

On networks with TLS inspection, put the proxy root CA (`*.crt`) in `certs/`; Docker builds import it. For local Gradle runs, point `JAVA_TOOL_OPTIONS` at a truststore containing it.

## Architecture

**Request path**: browser and the public tunnel both hit nginx on :3000. nginx serves the SPA and proxies `/api/*` to the backend on :8000. Alipay's notify URL (`/api/alipay/notify`) and return URL (`/pay/return`, an SPA page) both go through the tunnel. These two URLs are **not** env vars: they live in the `system_settings` table, are edited on the admin Settings page, and are read from the DB on every payment (no cache). Payment is refused while either is empty.

**No real auth**: login just looks up a username and the frontend stores `{id, username, role}` in localStorage; requests pass `userId` explicitly. Route guards are navigation convenience only. `/pay/return` must work without a login (it is loaded on the tunnel origin).

**Backend** (`backend/src/main/java/com/example/bookstore/`): feature packages (`auth book cart order payment refund fund settings`) plus:
- `alipay/` — `AlipayGateway` is the only entry point to Alipay; business code never touches the SDK directly (`SdkAlipayGateway` is the impl). Tests mock this interface; the sandbox is never called from tests.
- `scheduler/` — `PaymentCompensationJob` (every 60s, queries Alipay for `PENDING_PAYMENT` orders with `pay_requested_at` set) and `AutoCompleteJob` (PAID → COMPLETED 30 min after payment). Disabled with `app.scheduling.enabled=false`.
- `common/` — `ApiException` + global handler returning `{code, message}` with 400/404/409/502; `TimeConfig` provides a `Clock` bean (Asia/Shanghai).

**Key invariants** (keep these when changing code):
- Every order/refund state transition is a conditional update (`UPDATE ... WHERE id=? AND status=?`); zero rows = duplicate/concurrent, not an error. `OrderStateService` / `RefundStateService` hold these short DB transactions; **Alipay is never called inside a DB transaction** — services call Alipay first, then the state service.
- Fund transactions are idempotent via unique `(order_id, type)` (`insertIfAbsent`).
- Stock is deducted at order time (books locked `FOR UPDATE` in ascending id order) and restored on close or approved refund. Admin stock edits are deltas, never absolute values. Orders snapshot title and price.
- Never use `Instant.now()` etc.; inject `Clock` so tests can control time.
- Before acting on a pending order that has had a payment requested (continue pay, close), query Alipay first; `trade.query` is the source of truth (also for the return-check endpoint). Uncertain results → return an error without changing state.
- Schema changes go in a new Flyway migration under `src/main/resources/db/migration/` (never edit applied ones); Hibernate runs with `ddl-auto: validate`.

**Tests** (`backend/src/test/...`): integration tests only (`*IT`), all extending `support/IntegrationTestBase`: one shared PostgreSQL Testcontainer, `@MockitoBean AlipayGateway alipay`, a `MutableClock`, `TestApi` HTTP helper, and data reset before each test (truncates orders/cart/funds/refunds, clears settings, restores books). Helpers like `createOrder`, `forcePaid`, `configureAlipayUrls` exist there. Scheduled jobs are invoked directly in tests.

**Frontend** (`frontend/src/`): plain Vue 3 + Pinia + Axios, no UI library. `api/http.js` wraps Axios (base `/api`) and converts backend `{code, message}` errors into `Error` with `.code`/`.status`. Payment returns SDK-generated auto-submit form HTML that the frontend inserts and submits. Book covers are static files in `public/images/books/{id}.jpg`.

## Secrets

Alipay credentials go only in `.env` (copied from `info/info.txt`); both are gitignored. Never write key material into code, docs or images. `.dockerignore` is an allow-list — new files needed by Docker builds must be added there.
