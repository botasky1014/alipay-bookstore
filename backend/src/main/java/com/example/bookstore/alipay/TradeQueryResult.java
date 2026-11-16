package com.example.bookstore.alipay;

import java.math.BigDecimal;
import java.time.Instant;

public record TradeQueryResult(TradeState state, String tradeNo, BigDecimal totalAmount, Instant paidAt,
        String message) {

    public static TradeQueryResult of(TradeState state) {
        return new TradeQueryResult(state, null, null, null, null);
    }

    public static TradeQueryResult success(String tradeNo, BigDecimal totalAmount, Instant paidAt) {
        return new TradeQueryResult(TradeState.SUCCESS, tradeNo, totalAmount, paidAt, null);
    }

    public static TradeQueryResult unknown(String message) {
        return new TradeQueryResult(TradeState.UNKNOWN, null, null, null, message);
    }
}
