package com.example.bookstore.refund;

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

@Entity
@Table(name = "refund_requests")
public class RefundRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_id", nullable = false, unique = true)
    private Long orderId;

    @Column(name = "out_request_no", nullable = false, unique = true, length = 64)
    private String outRequestNo;

    @Column(nullable = false, length = 500)
    private String reason;

    @Column(name = "refund_amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal refundAmount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RefundStatus status;

    @Column(name = "last_error", length = 1000)
    private String lastError;

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected RefundRequest() {
    }

    public RefundRequest(Long orderId, String outRequestNo, String reason, BigDecimal refundAmount, Instant now) {
        this.orderId = orderId;
        this.outRequestNo = outRequestNo;
        this.reason = reason;
        this.refundAmount = refundAmount;
        this.status = RefundStatus.PENDING_REVIEW;
        this.createdAt = now;
    }

    public Long getId() {
        return id;
    }

    public Long getOrderId() {
        return orderId;
    }

    public String getOutRequestNo() {
        return outRequestNo;
    }

    public String getReason() {
        return reason;
    }

    public BigDecimal getRefundAmount() {
        return refundAmount;
    }

    public RefundStatus getStatus() {
        return status;
    }

    public String getLastError() {
        return lastError;
    }

    public Instant getReviewedAt() {
        return reviewedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
