package com.example.bookstore.alipay;

public enum RefundOutcome {
    SUCCESS,
    FAILED,
    /** Network error or system busy; the refund may or may not have happened. */
    UNKNOWN
}
