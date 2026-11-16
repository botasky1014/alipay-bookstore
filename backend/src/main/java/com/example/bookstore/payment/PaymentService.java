package com.example.bookstore.payment;

import org.springframework.stereotype.Service;

import com.example.bookstore.alipay.AlipayCallException;
import com.example.bookstore.alipay.AlipayGateway;
import com.example.bookstore.alipay.AlipayProperties;
import com.example.bookstore.alipay.PagePayRequest;
import com.example.bookstore.alipay.TradeQueryResult;
import com.example.bookstore.common.ApiException;
import com.example.bookstore.order.Order;
import com.example.bookstore.order.OrderDto;
import com.example.bookstore.order.OrderService;
import com.example.bookstore.order.OrderStateService;
import com.example.bookstore.order.OrderStatus;
import com.example.bookstore.settings.SettingsService;

/** Pay / continue payment (design 6.4) and user close (design 6.8). */
@Service
public class PaymentService {

    static final String CLOSED_BY_USER = "Closed by user";

    private final OrderService orderService;
    private final OrderStateService state;
    private final PaymentSyncService sync;
    private final SettingsService settings;
    private final AlipayGateway alipay;
    private final AlipayProperties properties;

    public PaymentService(OrderService orderService, OrderStateService state, PaymentSyncService sync,
            SettingsService settings, AlipayGateway alipay, AlipayProperties properties) {
        this.orderService = orderService;
        this.state = state;
        this.sync = sync;
        this.settings = settings;
        this.alipay = alipay;
        this.properties = properties;
    }

    public PayResponse pay(Long orderId, PayMethod method) {
        Order order = requirePending(orderId, "Only orders awaiting payment can be paid");

        if (order.getPayRequestedAt() != null) {
            TradeQueryResult result = alipay.query(order.getOrderNo());
            switch (result.state()) {
                case SUCCESS -> {
                    sync.applyQuery(order, result);
                    return new PayResponse(OrderStatus.PAID, null);
                }
                case CLOSED -> {
                    sync.applyClosed(order);
                    throw ApiException.conflict("PAYMENT_EXPIRED", "Payment expired, order closed");
                }
                case UNKNOWN -> throw ApiException.alipay(
                        "Could not check the payment status with Alipay. Please try again.");
                default -> {
                    // WAIT_BUYER_PAY or NOT_EXIST: the same out_trade_no can be paid again.
                }
            }
        }

        // Both URLs are read fresh on every payment, so a new tunnel address applies immediately.
        String notifyUrl = settings.getNotifyUrl();
        String returnUrl = settings.getReturnUrl();
        if (notifyUrl.isBlank() || returnUrl.isBlank()) {
            throw ApiException.conflict("ALIPAY_URLS_NOT_SET",
                    "Payment is unavailable: the administrator has not configured the Alipay notify and return URLs");
        }
        String form;
        try {
            form = alipay.pagePay(new PagePayRequest(order.getOrderNo(), order.getTotalAmount(),
                    "Bookstore Order " + order.getOrderNo(), notifyUrl, returnUrl,
                    properties.timeoutExpress(), method == PayMethod.QR));
        } catch (AlipayCallException e) {
            throw ApiException.alipay(e.getMessage());
        }
        state.markPayRequested(order.getId());
        return new PayResponse(OrderStatus.PENDING_PAYMENT, form);
    }

    public OrderDto close(Long orderId) {
        Order order = requirePending(orderId, "Only orders awaiting payment can be closed");

        if (order.getPayRequestedAt() != null) {
            TradeQueryResult result = alipay.query(order.getOrderNo());
            switch (result.state()) {
                case SUCCESS -> {
                    sync.applyQuery(order, result);
                    throw ApiException.conflict("ALREADY_PAID",
                            "This order has already been paid and cannot be closed");
                }
                case WAIT_BUYER_PAY -> {
                    try {
                        alipay.close(order.getOrderNo());
                    } catch (AlipayCallException e) {
                        throw ApiException.alipay(e.getMessage());
                    }
                }
                case UNKNOWN -> throw ApiException.alipay(
                        "Could not check the payment status with Alipay. Please try again.");
                default -> {
                    // NOT_EXIST or CLOSED: nothing to close at Alipay.
                }
            }
        }

        if (!state.closeAndRestoreStock(order.getId(), CLOSED_BY_USER)) {
            throw ApiException.conflict("INVALID_STATUS", "Order is no longer awaiting payment");
        }
        return orderService.get(order.getId());
    }

    private Order requirePending(Long orderId, String message) {
        Order order = orderService.require(orderId);
        if (order.getStatus() != OrderStatus.PENDING_PAYMENT) {
            throw ApiException.conflict("INVALID_STATUS", message);
        }
        return order;
    }
}
