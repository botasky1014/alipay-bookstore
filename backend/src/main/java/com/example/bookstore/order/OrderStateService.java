package com.example.bookstore.order;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.bookstore.common.ApiException;
import com.example.bookstore.fund.FundTransactionRepository;
import com.example.bookstore.fund.FundType;

/**
 * Database side of order status changes. Each method is one short transaction that uses a
 * conditional update, so it is safe to call repeatedly and concurrently. Alipay is never called
 * from inside these transactions.
 */
@Service
public class OrderStateService {

    /** Paid orders become COMPLETED this long after payment (design 6.7). */
    public static final Duration COMPLETE_AFTER = Duration.ofMinutes(30);

    private final OrderRepository orders;
    private final FundTransactionRepository funds;
    private final InventoryService inventory;
    private final Clock clock;

    public OrderStateService(OrderRepository orders, FundTransactionRepository funds, InventoryService inventory,
            Clock clock) {
        this.orders = orders;
        this.funds = funds;
        this.inventory = inventory;
        this.clock = clock;
    }

    /**
     * PENDING_PAYMENT -> PAID and records the PAYMENT_IN fund transaction.
     *
     * @return true if this call changed the order; false if it was not pending
     */
    @Transactional
    public boolean markPaid(Long orderId, String tradeNo, Instant paidAt) {
        Order order = require(orderId);
        Instant now = clock.instant();
        Instant at = paidAt != null ? paidAt : now;
        if (orders.markPaid(orderId, tradeNo, at, now) == 0) {
            return false;
        }
        funds.insertIfAbsent(FundType.PAYMENT_IN.name(), orderId, order.getTotalAmount(), tradeNo, at);
        return true;
    }

    /**
     * PENDING_PAYMENT -> CLOSED and returns the books to stock in the same transaction.
     *
     * @return true if this call closed the order; false if it was not pending
     */
    @Transactional
    public boolean closeAndRestoreStock(Long orderId, String reason) {
        if (orders.markClosed(orderId, reason, clock.instant()) == 0) {
            return false;
        }
        inventory.restoreStock(orderId);
        return true;
    }

    @Transactional
    public void markPayRequested(Long orderId) {
        if (orders.markPayRequested(orderId, clock.instant()) == 0) {
            throw ApiException.conflict("INVALID_STATUS", "Order is no longer awaiting payment");
        }
    }

    /** Completes every PAID order whose payment is at least 30 minutes old. */
    @Transactional
    public int completeExpiredPaidOrders() {
        Instant now = clock.instant();
        return orders.completePaidBefore(now.minus(COMPLETE_AFTER), now);
    }

    /**
     * Records money received for an order that was already closed locally.
     *
     * @return true the first time (the caller should then refund it)
     */
    @Transactional
    public boolean recordLatePayment(Long orderId, String tradeNo, Instant paidAt) {
        Order order = require(orderId);
        Instant at = paidAt != null ? paidAt : clock.instant();
        return funds.insertIfAbsent(FundType.PAYMENT_IN.name(), orderId, order.getTotalAmount(), tradeNo, at) == 1;
    }

    @Transactional
    public void recordAutoRefund(Long orderId, String tradeNo) {
        Order order = require(orderId);
        funds.insertIfAbsent(FundType.REFUND_OUT.name(), orderId, order.getTotalAmount(), tradeNo, clock.instant());
    }

    private Order require(Long orderId) {
        return orders.findById(orderId).orElseThrow(() -> ApiException.notFound("Order not found"));
    }
}
