package com.example.bookstore.refund;

import java.time.Clock;
import java.time.Instant;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.bookstore.common.ApiException;
import com.example.bookstore.fund.FundTransactionRepository;
import com.example.bookstore.fund.FundType;
import com.example.bookstore.order.InventoryService;
import com.example.bookstore.order.Order;
import com.example.bookstore.order.OrderRepository;
import com.example.bookstore.order.OrderStatus;

/** Database side of refunds; each method is one transaction with conditional updates. */
@Service
public class RefundStateService {

    private final RefundRequestRepository refunds;
    private final OrderRepository orders;
    private final FundTransactionRepository funds;
    private final InventoryService inventory;
    private final Clock clock;

    public RefundStateService(RefundRequestRepository refunds, OrderRepository orders,
            FundTransactionRepository funds, InventoryService inventory, Clock clock) {
        this.refunds = refunds;
        this.orders = orders;
        this.funds = funds;
        this.inventory = inventory;
        this.clock = clock;
    }

    /** PAID -> REFUNDING and creates the PENDING_REVIEW request with a fixed out_request_no. */
    @Transactional
    public RefundRequest create(Order order, String reason) {
        if (refunds.findByOrderId(order.getId()).isPresent()) {
            throw ApiException.conflict("ALREADY_REQUESTED", "A refund has already been requested for this order");
        }
        Instant now = clock.instant();
        if (orders.transition(order.getId(), OrderStatus.PAID, OrderStatus.REFUNDING, now) == 0) {
            throw ApiException.conflict("INVALID_STATUS", "Only paid orders can be refunded");
        }
        return refunds.save(new RefundRequest(order.getId(), "RF" + order.getOrderNo(), reason.trim(),
                order.getTotalAmount(), now));
    }

    /** After Alipay confirmed the refund: request and order REFUNDED, stock restored, REFUND_OUT recorded. */
    @Transactional
    public void completeRefund(RefundRequest request, Order order) {
        Instant now = clock.instant();
        if (refunds.review(request.getId(), RefundStatus.REFUNDED, now) == 0) {
            return; // A concurrent approval already finished it.
        }
        if (orders.transition(order.getId(), OrderStatus.REFUNDING, OrderStatus.REFUNDED, now) == 0) {
            throw new IllegalStateException("Order " + order.getOrderNo() + " is not in REFUNDING status");
        }
        inventory.restoreStock(order.getId());
        funds.insertIfAbsent(FundType.REFUND_OUT.name(), order.getId(), request.getRefundAmount(),
                order.getAlipayTradeNo(), now);
    }

    @Transactional
    public void recordError(Long requestId, String error) {
        String message = error == null ? "Unknown error" : error;
        refunds.recordError(requestId, message.length() > 1000 ? message.substring(0, 1000) : message);
    }

    /** Request REJECTED, order REFUNDING -> COMPLETED. */
    @Transactional
    public void reject(RefundRequest request) {
        Instant now = clock.instant();
        if (refunds.review(request.getId(), RefundStatus.REJECTED, now) == 0) {
            throw ApiException.conflict("INVALID_STATUS", "This refund request has already been reviewed");
        }
        if (orders.markCompleted(request.getOrderId(), OrderStatus.REFUNDING, now) == 0) {
            throw new IllegalStateException("Order " + request.getOrderId() + " is not in REFUNDING status");
        }
    }
}
