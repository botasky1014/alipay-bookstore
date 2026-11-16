package com.example.bookstore.fund;

import java.math.BigDecimal;
import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Rows are inserted only through {@link FundTransactionRepository#insertIfAbsent}. */
@Entity
@Table(name = "fund_transactions")
public class FundTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private FundType type;

    @Column(name = "order_id", nullable = false)
    private Long orderId;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal amount;

    @Column(name = "alipay_trade_no", length = 64)
    private String alipayTradeNo;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    protected FundTransaction() {
    }

    public Long getId() {
        return id;
    }

    public FundType getType() {
        return type;
    }

    public Long getOrderId() {
        return orderId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getAlipayTradeNo() {
        return alipayTradeNo;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }
}
