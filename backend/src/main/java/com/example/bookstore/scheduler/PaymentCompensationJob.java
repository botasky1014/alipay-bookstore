package com.example.bookstore.scheduler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.example.bookstore.alipay.AlipayGateway;
import com.example.bookstore.order.Order;
import com.example.bookstore.order.OrderRepository;
import com.example.bookstore.order.OrderStatus;
import com.example.bookstore.payment.PaymentSyncService;

/**
 * Recovers from lost notifications (for example when the tunnel was down): queries Alipay for every
 * pending order that has been sent to Alipay and applies the result (design 6.7).
 */
@Component
public class PaymentCompensationJob {

    private static final Logger log = LoggerFactory.getLogger(PaymentCompensationJob.class);

    private final OrderRepository orders;
    private final AlipayGateway alipay;
    private final PaymentSyncService sync;

    public PaymentCompensationJob(OrderRepository orders, AlipayGateway alipay, PaymentSyncService sync) {
        this.orders = orders;
        this.alipay = alipay;
        this.sync = sync;
    }

    @Scheduled(initialDelay = 30_000, fixedDelay = 60_000)
    public void run() {
        for (Order order : orders.findByStatusAndPayRequestedAtIsNotNullOrderByIdAsc(OrderStatus.PENDING_PAYMENT)) {
            try {
                sync.applyQuery(order, alipay.query(order.getOrderNo()));
            } catch (RuntimeException e) {
                log.warn("Payment compensation failed for order {}", order.getOrderNo(), e);
            }
        }
    }
}
