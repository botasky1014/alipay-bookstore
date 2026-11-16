package com.example.bookstore.alipay;

public record RefundResult(RefundOutcome outcome, String message) {

    public static RefundResult success() {
        return new RefundResult(RefundOutcome.SUCCESS, null);
    }

    public static RefundResult failed(String message) {
        return new RefundResult(RefundOutcome.FAILED, message);
    }

    public static RefundResult unknown(String message) {
        return new RefundResult(RefundOutcome.UNKNOWN, message);
    }
}
