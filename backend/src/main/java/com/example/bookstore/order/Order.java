package com.example.bookstore.order;

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

/**
 * Status changes never go through setters; they use the conditional updates in
 * {@link OrderRepository} so concurrent transitions cannot overwrite each other.
 */
@Entity
@Table(name = "orders")
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_no", nullable = false, unique = true, length = 32)
    private String orderNo;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "total_amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal totalAmount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OrderStatus status;

    @Column(name = "alipay_trade_no", length = 64)
    private String alipayTradeNo;

    @Column(name = "close_reason")
    private String closeReason;

    @Column(name = "pay_requested_at")
    private Instant payRequestedAt;

    @Column(name = "paid_at")
    private Instant paidAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "closed_at")
    private Instant closedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Order() {
    }

    public Order(String orderNo, Long userId, BigDecimal totalAmount, Instant now) {
        this.orderNo = orderNo;
        this.userId = userId;
        this.totalAmount = totalAmount;
        this.status = OrderStatus.PENDING_PAYMENT;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public Long getId() {
        return id;
    }

    public String getOrderNo() {
        return orderNo;
    }

    public Long getUserId() {
        return userId;
    }

    public BigDecimal getTotalAmount() {
        return totalAmount;
    }

    public OrderStatus getStatus() {
        return status;
    }

    public String getAlipayTradeNo() {
        return alipayTradeNo;
    }

    public String getCloseReason() {
        return closeReason;
    }

    public Instant getPayRequestedAt() {
        return payRequestedAt;
    }

    public Instant getPaidAt() {
        return paidAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public Instant getClosedAt() {
        return closedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
