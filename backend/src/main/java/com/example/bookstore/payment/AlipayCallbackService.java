package com.example.bookstore.payment;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.example.bookstore.alipay.AlipayGateway;
import com.example.bookstore.alipay.AlipayProperties;
import com.example.bookstore.common.ApiException;
import com.example.bookstore.common.TimeConfig;
import com.example.bookstore.order.Order;
import com.example.bookstore.order.OrderDto;
import com.example.bookstore.order.OrderRepository;
import com.example.bookstore.order.OrderService;
import com.example.bookstore.order.OrderStatus;

/** Handles the asynchronous notification (design 6.5) and the synchronous return (design 6.6). */
@Service
public class AlipayCallbackService {

    private static final Logger log = LoggerFactory.getLogger(AlipayCallbackService.class);
    private static final DateTimeFormatter ALIPAY_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final AlipayGateway alipay;
    private final AlipayProperties properties;
    private final OrderRepository orders;
    private final OrderService orderService;
    private final PaymentSyncService sync;

    public AlipayCallbackService(AlipayGateway alipay, AlipayProperties properties, OrderRepository orders,
            OrderService orderService, PaymentSyncService sync) {
        this.alipay = alipay;
        this.properties = properties;
        this.orders = orders;
        this.orderService = orderService;
        this.sync = sync;
    }

    /**
     * @return true if Alipay should be told "success" (processed or safely ignored), false for "failure"
     */
    public boolean handleNotification(Map<String, String> params) {
        String outTradeNo = params.get("out_trade_no");
        if (!alipay.verifySignature(params)) {
            log.warn("Rejected notification for {}: invalid signature", outTradeNo);
            return false;
        }
        if (!properties.appId().equals(params.get("app_id"))
                || (params.get("seller_id") != null && !properties.sellerId().equals(params.get("seller_id")))) {
            log.warn("Rejected notification for {}: app_id or seller_id mismatch", outTradeNo);
            return false;
        }
        Optional<Order> found = outTradeNo == null ? Optional.empty() : orders.findByOrderNo(outTradeNo);
        if (found.isEmpty()) {
            log.warn("Rejected notification: unknown out_trade_no {}", outTradeNo);
            return false;
        }
        Order order = found.get();
        if (!amountMatches(params.get("total_amount"), order.getTotalAmount())) {
            log.warn("Rejected notification for {}: amount {} does not match order amount {}", outTradeNo,
                    params.get("total_amount"), order.getTotalAmount());
            return false;
        }

        String tradeStatus = params.get("trade_status");
        if ("TRADE_SUCCESS".equals(tradeStatus) || "TRADE_FINISHED".equals(tradeStatus)) {
            sync.applyPaid(order, params.get("trade_no"), parseTime(params.get("gmt_payment")));
        } else if ("TRADE_CLOSED".equals(tradeStatus) && order.getStatus() == OrderStatus.PENDING_PAYMENT) {
            sync.applyClosed(order);
        }
        return true;
    }

    /** Verifies the return parameters, then trusts only alipay.trade.query to update the order. */
    public OrderDto handleReturn(Map<String, String> params) {
        if (!alipay.verifySignature(params) || !properties.appId().equals(params.get("app_id"))) {
            throw ApiException.badRequest("INVALID_SIGNATURE", "Invalid payment return parameters");
        }
        Order order = Optional.ofNullable(params.get("out_trade_no")).flatMap(orders::findByOrderNo)
                .orElseThrow(() -> ApiException.notFound("Order not found"));
        if (order.getStatus() == OrderStatus.PENDING_PAYMENT) {
            sync.applyQuery(order, alipay.query(order.getOrderNo()));
        }
        return orderService.get(order.getId());
    }

    private static boolean amountMatches(String value, BigDecimal expected) {
        try {
            return value != null && new BigDecimal(value).compareTo(expected) == 0;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private static Instant parseTime(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return LocalDateTime.parse(value, ALIPAY_TIME).atZone(TimeConfig.ZONE).toInstant();
        } catch (DateTimeParseException e) {
            return null;
        }
    }
}
