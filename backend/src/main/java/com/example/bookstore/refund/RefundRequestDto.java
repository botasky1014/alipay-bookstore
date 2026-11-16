package com.example.bookstore.refund;

import java.math.BigDecimal;
import java.time.Instant;

import com.example.bookstore.order.OrderDto;

public record RefundRequestDto(Long id, RefundStatus status, String reason, BigDecimal refundAmount,
        String lastError, Instant createdAt, Instant reviewedAt, OrderDto order) {

    public static RefundRequestDto from(RefundRequest request, OrderDto order) {
        return new RefundRequestDto(request.getId(), request.getStatus(), request.getReason(),
                request.getRefundAmount(), request.getLastError(), request.getCreatedAt(), request.getReviewedAt(),
                order);
    }
}
