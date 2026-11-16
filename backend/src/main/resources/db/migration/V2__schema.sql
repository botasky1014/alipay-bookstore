-- Core schema. See doc/design.md section 4.

CREATE TABLE users (
    id       BIGSERIAL PRIMARY KEY,
    username VARCHAR(50) NOT NULL UNIQUE,
    role     VARCHAR(20) NOT NULL CHECK (role IN ('USER', 'ADMIN'))
);

CREATE TABLE books (
    id          BIGSERIAL PRIMARY KEY,
    title       VARCHAR(255)   NOT NULL,
    author      VARCHAR(255)   NOT NULL,
    description VARCHAR(2000)  NOT NULL,
    cover_image VARCHAR(255)   NOT NULL,
    price       NUMERIC(10, 2) NOT NULL CHECK (price > 0),
    stock       INTEGER        NOT NULL CHECK (stock >= 0),
    updated_at  TIMESTAMPTZ    NOT NULL
);

CREATE TABLE cart_items (
    id         BIGSERIAL PRIMARY KEY,
    user_id    BIGINT      NOT NULL REFERENCES users (id),
    book_id    BIGINT      NOT NULL REFERENCES books (id),
    quantity   INTEGER     NOT NULL CHECK (quantity > 0),
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uk_cart_items_user_book UNIQUE (user_id, book_id)
);

CREATE TABLE orders (
    id               BIGSERIAL PRIMARY KEY,
    order_no         VARCHAR(32)    NOT NULL UNIQUE,
    user_id          BIGINT         NOT NULL REFERENCES users (id),
    total_amount     NUMERIC(10, 2) NOT NULL CHECK (total_amount > 0),
    status           VARCHAR(20)    NOT NULL CHECK (status IN
        ('PENDING_PAYMENT', 'PAID', 'REFUNDING', 'REFUNDED', 'CLOSED', 'COMPLETED')),
    alipay_trade_no  VARCHAR(64),
    close_reason     VARCHAR(255),
    pay_requested_at TIMESTAMPTZ,
    paid_at          TIMESTAMPTZ,
    completed_at     TIMESTAMPTZ,
    closed_at        TIMESTAMPTZ,
    created_at       TIMESTAMPTZ    NOT NULL,
    updated_at       TIMESTAMPTZ    NOT NULL
);

CREATE INDEX idx_orders_user_id ON orders (user_id);
CREATE INDEX idx_orders_status ON orders (status);

CREATE TABLE order_items (
    id                  BIGSERIAL PRIMARY KEY,
    order_id            BIGINT         NOT NULL REFERENCES orders (id),
    book_id             BIGINT         NOT NULL REFERENCES books (id),
    title_snapshot      VARCHAR(255)   NOT NULL,
    unit_price_snapshot NUMERIC(10, 2) NOT NULL,
    quantity            INTEGER        NOT NULL CHECK (quantity > 0),
    subtotal            NUMERIC(10, 2) NOT NULL
);

CREATE INDEX idx_order_items_order_id ON order_items (order_id);

CREATE TABLE refund_requests (
    id             BIGSERIAL PRIMARY KEY,
    order_id       BIGINT         NOT NULL UNIQUE REFERENCES orders (id),
    out_request_no VARCHAR(64)    NOT NULL UNIQUE,
    reason         VARCHAR(500)   NOT NULL,
    refund_amount  NUMERIC(10, 2) NOT NULL,
    status         VARCHAR(20)    NOT NULL CHECK (status IN ('PENDING_REVIEW', 'REFUNDED', 'REJECTED')),
    last_error     VARCHAR(1000),
    reviewed_at    TIMESTAMPTZ,
    created_at     TIMESTAMPTZ    NOT NULL
);

CREATE TABLE fund_transactions (
    id              BIGSERIAL PRIMARY KEY,
    type            VARCHAR(20)    NOT NULL CHECK (type IN ('PAYMENT_IN', 'REFUND_OUT')),
    order_id        BIGINT         NOT NULL REFERENCES orders (id),
    amount          NUMERIC(10, 2) NOT NULL CHECK (amount > 0),
    alipay_trade_no VARCHAR(64),
    occurred_at     TIMESTAMPTZ    NOT NULL,
    CONSTRAINT uk_fund_transactions_order_type UNIQUE (order_id, type)
);

-- Column names avoid the SQL keywords KEY and VALUE.
CREATE TABLE system_settings (
    setting_key   VARCHAR(100)  PRIMARY KEY,
    setting_value VARCHAR(1000) NOT NULL,
    updated_at    TIMESTAMPTZ   NOT NULL
);
