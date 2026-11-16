package com.example.bookstore;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.example.bookstore.alipay.TradeQueryResult;
import com.example.bookstore.alipay.TradeState;
import com.example.bookstore.scheduler.AutoCompleteJob;
import com.example.bookstore.scheduler.PaymentCompensationJob;
import com.example.bookstore.support.IntegrationTestBase;

class ScheduledJobsIT extends IntegrationTestBase {

    @Autowired
    PaymentCompensationJob compensationJob;

    @Autowired
    AutoCompleteJob autoCompleteJob;

    private long createOrderSentToAlipay(long bookId) {
        configureAlipayUrls();
        when(alipay.pagePay(any())).thenReturn("<form></form>");
        long orderId = createOrder(bookId, 1);
        api.post("/api/orders/" + orderId + "/pay", Map.of("method", "WEB"));
        return orderId;
    }

    @Test
    void compensationAppliesQueryResults() {
        long paid = createOrderSentToAlipay(1);
        long closed = createOrderSentToAlipay(2);
        long waiting = createOrderSentToAlipay(3);
        long neverSent = createOrder(4, 1);
        when(alipay.query(orderNo(paid))).thenReturn(
                TradeQueryResult.success("T1", new BigDecimal("39.90"), Instant.parse("2026-11-16T02:01:00Z")));
        when(alipay.query(orderNo(closed))).thenReturn(TradeQueryResult.of(TradeState.CLOSED));
        when(alipay.query(orderNo(waiting))).thenReturn(TradeQueryResult.of(TradeState.WAIT_BUYER_PAY));

        compensationJob.run();

        assertThat(orderStatus(paid)).isEqualTo("PAID");
        assertThat(fundCount("PAYMENT_IN")).isEqualTo(1);
        assertThat(orderStatus(closed)).isEqualTo("CLOSED");
        assertThat(stockOf(2)).isEqualTo(50);
        assertThat(orderStatus(waiting)).isEqualTo("PENDING_PAYMENT");
        assertThat(orderStatus(neverSent)).isEqualTo("PENDING_PAYMENT");
    }

    @Test
    void paidOrdersCompleteThirtyMinutesAfterPayment() {
        long orderId = createOrder(1, 1);
        forcePaid(orderId, START);

        clock.set(START.plus(Duration.ofMinutes(29)).plusSeconds(59));
        autoCompleteJob.run();
        assertThat(orderStatus(orderId)).isEqualTo("PAID");

        clock.set(START.plus(Duration.ofMinutes(30)));
        autoCompleteJob.run();
        assertThat(orderStatus(orderId)).isEqualTo("COMPLETED");
    }

    @Test
    void refundingOrdersAreNotAutoCompleted() {
        long orderId = createOrder(1, 1);
        forcePaid(orderId, START);
        api.post("/api/orders/" + orderId + "/refund-requests", Map.of("reason", "Changed my mind"));

        clock.set(START.plus(Duration.ofHours(2)));
        autoCompleteJob.run();

        assertThat(orderStatus(orderId)).isEqualTo("REFUNDING");
    }
}
