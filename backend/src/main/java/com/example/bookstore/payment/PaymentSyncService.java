package com.example.bookstore.payment;

import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.example.bookstore.alipay.AlipayGateway;
import com.example.bookstore.alipay.RefundOutcome;
import com.example.bookstore.alipay.RefundResult;
import com.example.bookstore.alipay.TradeQueryResult;
import com.example.bookstore.order.Order;
import com.example.bookstore.order.OrderRepository;
import com.example.bookstore.order.OrderStateService;
import com.example.bookstore.order.OrderStatus;

/**
 * Applies what Alipay reports (query result or notification) to a local order. Used by the
 * notification handler, the return check, pay/continue, close, and the compensation job.
 */
@Service
public class PaymentSyncService {

    private static final Logger log = LoggerFactory.getLogger(PaymentSyncService.class);

    static final String CLOSED_BY_ALIPAY = "Payment expired: Alipay closed the transaction";

    private final OrderStateService state;
    private final OrderRepository orders;
    private final AlipayGateway alipay;

    public PaymentSyncService(OrderStateService state, OrderRepository orders, AlipayGateway alipay) {
        this.state = state;
        this.orders = orders;
        this.alipay = alipay;
    }

    /** SUCCESS marks the order paid; CLOSED closes it and restores stock; anything else changes nothing. */
    public void applyQuery(Order order, TradeQueryResult result) {
        switch (result.state()) {
            case SUCCESS -> applyPaid(order, result.tradeNo(), result.paidAt());
            case CLOSED -> state.closeAndRestoreStock(order.getId(), CLOSED_BY_ALIPAY);
            default -> {
                // WAIT_BUYER_PAY, NOT_EXIST, UNKNOWN: nothing to apply.
            }
        }
    }

    public void applyClosed(Order order) {
        state.closeAndRestoreStock(order.getId(), CLOSED_BY_ALIPAY);
    }

    /** Marks the order paid. If it was already closed locally, the money is refunded automatically. */
    public void applyPaid(Order order, String tradeNo, Instant paidAt) {
        if (state.markPaid(order.getId(), tradeNo, paidAt)) {
            log.info("Order {} paid (Alipay trade {})", order.getOrderNo(), tradeNo);
            return;
        }
        OrderStatus current = orders.findById(order.getId()).map(Order::getStatus).orElse(null);
        if (current == OrderStatus.CLOSED) {
            refundLatePayment(order, tradeNo, paidAt);
        }
    }

    /** Edge case from design 6.5: payment arrived for an order that is already closed. */
    private void refundLatePayment(Order order, String tradeNo, Instant paidAt) {
        if (!state.recordLatePayment(order.getId(), tradeNo, paidAt)) {
            return; // Already handled by an earlier notification.
        }
        log.warn("Order {} was paid after it was closed; refunding automatically", order.getOrderNo());
        String outRequestNo = "AUTO" + order.getOrderNo();
        RefundResult result = alipay.refund(order.getOrderNo(), order.getTotalAmount(), outRequestNo,
                "Order was closed before payment arrived");
        if (result.outcome() == RefundOutcome.UNKNOWN) {
            result = alipay.refundQuery(order.getOrderNo(), outRequestNo);
        }
        if (result.outcome() == RefundOutcome.SUCCESS) {
            state.recordAutoRefund(order.getId(), tradeNo);
        } else {
            log.warn("Automatic refund for closed order {} failed: {}. Refund it manually in the Alipay console.",
                    order.getOrderNo(), result.message());
        }
    }
}
