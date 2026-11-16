package com.example.bookstore.alipay;

/** Normalized result of {@code alipay.trade.query}. */
public enum TradeState {
    /** TRADE_SUCCESS or TRADE_FINISHED. */
    SUCCESS,
    /** The trade exists and is waiting for the buyer to pay. */
    WAIT_BUYER_PAY,
    /** TRADE_CLOSED: timed out unpaid, closed, or fully refunded. */
    CLOSED,
    /** ACQ.TRADE_NOT_EXIST: the buyer never scanned or logged in on the cashier page. */
    NOT_EXIST,
    /** Network error or unexpected response; nothing can be concluded. */
    UNKNOWN
}
