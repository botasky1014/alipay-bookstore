package com.example.bookstore;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import com.example.bookstore.alipay.TradeQueryResult;
import com.example.bookstore.alipay.TradeState;
import com.example.bookstore.support.IntegrationTestBase;

class CloseOrderIT extends IntegrationTestBase {

    private long createOrderSentToAlipay() {
        configureAlipayUrls();
        when(alipay.pagePay(any())).thenReturn("<form></form>");
        long orderId = createOrder(1, 2);
        api.post("/api/orders/" + orderId + "/pay", Map.of("method", "WEB"));
        return orderId;
    }

    @Test
    void closingANeverPaidOrderRestoresStockWithoutCallingAlipay() {
        long orderId = createOrder(1, 2);
        assertThat(stockOf(1)).isEqualTo(48);

        var response = api.post("/api/orders/" + orderId + "/close", null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsEntry("status", "CLOSED").containsEntry("closeReason", "Closed by user");
        assertThat(stockOf(1)).isEqualTo(50);
        verify(alipay, never()).query(anyString());
    }

    @Test
    void closingAWaitingTradeClosesItAtAlipayFirst() {
        long orderId = createOrderSentToAlipay();
        when(alipay.query(anyString())).thenReturn(TradeQueryResult.of(TradeState.WAIT_BUYER_PAY));

        api.post("/api/orders/" + orderId + "/close", null);

        verify(alipay).close(orderNo(orderId));
        assertThat(orderStatus(orderId)).isEqualTo("CLOSED");
        assertThat(stockOf(1)).isEqualTo(50);
    }

    @Test
    void closeIsRejectedWhenAlipayShowsTheOrderPaid() {
        long orderId = createOrderSentToAlipay();
        when(alipay.query(anyString())).thenReturn(
                TradeQueryResult.success("T1", new BigDecimal("79.80"), Instant.parse("2026-11-16T02:01:00Z")));

        var response = api.post("/api/orders/" + orderId + "/close", null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(orderStatus(orderId)).isEqualTo("PAID");
        assertThat(stockOf(1)).isEqualTo(48);
    }

    @Test
    void unknownQueryResultLeavesTheOrderUnchanged() {
        long orderId = createOrderSentToAlipay();
        when(alipay.query(anyString())).thenReturn(TradeQueryResult.unknown("timeout"));

        var response = api.post("/api/orders/" + orderId + "/close", null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);
        assertThat(orderStatus(orderId)).isEqualTo("PENDING_PAYMENT");
        assertThat(stockOf(1)).isEqualTo(48);
    }

    @Test
    void cannotCloseAPaidOrder() {
        long orderId = createOrder(1, 1);
        forcePaid(orderId, START);

        assertThat(api.post("/api/orders/" + orderId + "/close", null).getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT);
    }
}
