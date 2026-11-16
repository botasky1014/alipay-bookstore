package com.example.bookstore.fund;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * @param alipayBalance null when the balance query is unavailable
 * @param difference    alipayBalance - localNetTotal, null when the balance is unavailable
 */
public record FundSummaryDto(BigDecimal totalIn, BigDecimal totalOut, BigDecimal localNetTotal,
        BigDecimal alipayBalance, BigDecimal difference, List<Transaction> transactions) {

    public record Transaction(Long id, FundType type, Long orderId, String orderNo, BigDecimal amount,
            String alipayTradeNo, Instant occurredAt) {
    }
}
