package com.example.bookstore;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;

import com.example.bookstore.alipay.PagePayRequest;
import com.example.bookstore.alipay.RefundResult;
import com.example.bookstore.alipay.TradeQueryResult;
import com.example.bookstore.alipay.TradeState;
import com.example.bookstore.support.IntegrationTestBase;

class PaymentIT extends IntegrationTestBase {

    @BeforeEach
    void configure() {
        configureAlipayUrls();
        when(alipay.pagePay(any())).thenReturn("<form id=\"alipay\"></form>");
        when(alipay.verifySignature(anyMap())).thenReturn(true);
    }

    private Map<String, String> notification(long orderId, String amount, String tradeStatus) {
        Map<String, String> params = new HashMap<>();
        params.put("app_id", APP_ID);
        params.put("seller_id", SELLER_ID);
        params.put("out_trade_no", orderNo(orderId));
        params.put("trade_no", "2026111622001");
        params.put("total_amount", amount);
        params.put("trade_status", tradeStatus);
        params.put("gmt_payment", "2026-11-16 10:05:00");
        params.put("sign", "signature");
        return params;
    }

    // ----- pay / continue payment -----

    @Test
    void firstPaymentReturnsFormWithCurrentSettings() {
        long orderId = createOrder(1, 2);

        var response = api.post("/api/orders/" + orderId + "/pay", null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsEntry("status", "PENDING_PAYMENT")
                .containsEntry("formHtml", "<form id=\"alipay\"></form>");
        ArgumentCaptor<PagePayRequest> captor = ArgumentCaptor.forClass(PagePayRequest.class);
        verify(alipay).pagePay(captor.capture());
        PagePayRequest request = captor.getValue();
        assertThat(request.outTradeNo()).isEqualTo(orderNo(orderId));
        assertThat(request.totalAmount()).isEqualByComparingTo("79.80");
        assertThat(request.notifyUrl()).isEqualTo(NOTIFY_URL);
        assertThat(request.returnUrl()).isEqualTo(RETURN_URL);
        assertThat(request.timeoutExpress()).isEqualTo("2h");
        verify(alipay, never()).query(anyString());
        assertThat(jdbc.queryForObject("SELECT pay_requested_at IS NOT NULL FROM orders WHERE id = ?",
                Boolean.class, orderId)).isTrue();
    }

    @Test
    void newUrlsAreUsedOnTheNextPayment() {
        long orderId = createOrder(1, 1);
        api.post("/api/orders/" + orderId + "/pay", null);
        configureAlipayUrls("https://second.tunnel.example/api/alipay/notify", "https://second.tunnel.example/pay/return");
        when(alipay.query(anyString())).thenReturn(TradeQueryResult.of(TradeState.WAIT_BUYER_PAY));

        api.post("/api/orders/" + orderId + "/pay", null);

        ArgumentCaptor<PagePayRequest> captor = ArgumentCaptor.forClass(PagePayRequest.class);
        verify(alipay, org.mockito.Mockito.times(2)).pagePay(captor.capture());
        PagePayRequest second = captor.getAllValues().get(1);
        assertThat(second.notifyUrl()).isEqualTo("https://second.tunnel.example/api/alipay/notify");
        assertThat(second.returnUrl()).isEqualTo("https://second.tunnel.example/pay/return");
        assertThat(second.outTradeNo()).isEqualTo(orderNo(orderId));
    }

    @Test
    void paymentIsRejectedWhenAnAlipayUrlIsNotConfigured() {
        long orderId = createOrder(1, 1);
        for (String key : new String[] {"alipay.notify_url", "alipay.return_url"}) {
            configureAlipayUrls();
            jdbc.update("UPDATE system_settings SET setting_value = '' WHERE setting_key = ?", key);

            var response = api.post("/api/orders/" + orderId + "/pay", null);

            assertThat(response.getStatusCode()).as(key).isEqualTo(HttpStatus.CONFLICT);
            assertThat(response.getBody()).containsEntry("code", "ALIPAY_URLS_NOT_SET");
        }
        verify(alipay, never()).pagePay(any());
    }

    @Test
    void continuePaymentOnClosedTradeClosesOrderAndRestoresStock() {
        long orderId = createOrder(1, 3);
        api.post("/api/orders/" + orderId + "/pay", null);
        when(alipay.query(anyString())).thenReturn(TradeQueryResult.of(TradeState.CLOSED));

        var response = api.post("/api/orders/" + orderId + "/pay", null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).containsEntry("message", "Payment expired, order closed");
        assertThat(orderStatus(orderId)).isEqualTo("CLOSED");
        assertThat(stockOf(1)).isEqualTo(50);
    }

    @Test
    void continuePaymentOnPaidTradeMarksOrderPaid() {
        long orderId = createOrder(1, 1);
        api.post("/api/orders/" + orderId + "/pay", null);
        when(alipay.query(anyString())).thenReturn(
                TradeQueryResult.success("T1", new BigDecimal("39.90"), Instant.parse("2026-11-16T02:01:00Z")));

        var response = api.post("/api/orders/" + orderId + "/pay", null);

        assertThat(response.getBody()).containsEntry("status", "PAID");
        assertThat(orderStatus(orderId)).isEqualTo("PAID");
        assertThat(fundCount("PAYMENT_IN")).isEqualTo(1);
    }

    @Test
    void continuePaymentFailsWhenAlipayCannotBeReached() {
        long orderId = createOrder(1, 1);
        api.post("/api/orders/" + orderId + "/pay", null);
        when(alipay.query(anyString())).thenReturn(TradeQueryResult.unknown("timeout"));

        var response = api.post("/api/orders/" + orderId + "/pay", null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);
        assertThat(orderStatus(orderId)).isEqualTo("PENDING_PAYMENT");
    }

    // ----- asynchronous notification -----

    @Test
    void validNotificationMarksOrderPaidOnce() {
        long orderId = createOrder(1, 1);
        Map<String, String> params = notification(orderId, "39.90", "TRADE_SUCCESS");

        assertThat(api.postForm("/api/alipay/notify", params).getBody()).isEqualTo("success");
        assertThat(api.postForm("/api/alipay/notify", params).getBody()).isEqualTo("success");

        assertThat(orderStatus(orderId)).isEqualTo("PAID");
        assertThat(fundCount("PAYMENT_IN")).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT paid_at FROM orders WHERE id = ?", java.sql.Timestamp.class, orderId)
                .toInstant()).isEqualTo(Instant.parse("2026-11-16T02:05:00Z"));
        assertThat(jdbc.queryForObject("SELECT alipay_trade_no FROM orders WHERE id = ?", String.class, orderId))
                .isEqualTo("2026111622001");
    }

    @Test
    void notificationWithBadSignatureIsRejected() {
        long orderId = createOrder(1, 1);
        when(alipay.verifySignature(anyMap())).thenReturn(false);

        assertThat(api.postForm("/api/alipay/notify", notification(orderId, "39.90", "TRADE_SUCCESS")).getBody())
                .isEqualTo("failure");
        assertThat(orderStatus(orderId)).isEqualTo("PENDING_PAYMENT");
    }

    @Test
    void notificationWithWrongAmountOrAppIsRejected() {
        long orderId = createOrder(1, 1);

        assertThat(api.postForm("/api/alipay/notify", notification(orderId, "0.01", "TRADE_SUCCESS")).getBody())
                .isEqualTo("failure");
        Map<String, String> wrongApp = notification(orderId, "39.90", "TRADE_SUCCESS");
        wrongApp.put("app_id", "other-app");
        assertThat(api.postForm("/api/alipay/notify", wrongApp).getBody()).isEqualTo("failure");

        assertThat(orderStatus(orderId)).isEqualTo("PENDING_PAYMENT");
        assertThat(fundCount("PAYMENT_IN")).isZero();
    }

    @Test
    void paymentForAClosedOrderIsRefundedAutomatically() {
        long orderId = createOrder(1, 1);
        api.post("/api/orders/" + orderId + "/close", null);
        when(alipay.refund(anyString(), any(), anyString(), anyString())).thenReturn(RefundResult.success());

        assertThat(api.postForm("/api/alipay/notify", notification(orderId, "39.90", "TRADE_SUCCESS")).getBody())
                .isEqualTo("success");
        api.postForm("/api/alipay/notify", notification(orderId, "39.90", "TRADE_SUCCESS"));

        assertThat(orderStatus(orderId)).isEqualTo("CLOSED");
        assertThat(fundCount("PAYMENT_IN")).isEqualTo(1);
        assertThat(fundCount("REFUND_OUT")).isEqualTo(1);
        verify(alipay).refund(eq(orderNo(orderId)), any(), eq("AUTO" + orderNo(orderId)), anyString());
    }

    // ----- synchronous return -----

    @Test
    void returnCheckTrustsTheQueryResult() {
        long orderId = createOrder(1, 1);
        api.post("/api/orders/" + orderId + "/pay", null);
        Map<String, String> params = Map.of("app_id", APP_ID, "out_trade_no", orderNo(orderId), "sign", "x");

        when(alipay.query(anyString())).thenReturn(TradeQueryResult.of(TradeState.WAIT_BUYER_PAY));
        assertThat(api.post("/api/alipay/return-check", params).getBody()).containsEntry("status", "PENDING_PAYMENT");

        when(alipay.query(anyString())).thenReturn(
                TradeQueryResult.success("T1", new BigDecimal("39.90"), Instant.parse("2026-11-16T02:01:00Z")));
        assertThat(api.post("/api/alipay/return-check", params).getBody()).containsEntry("status", "PAID");
    }

    @Test
    void returnCheckRejectsBadSignature() {
        long orderId = createOrder(1, 1);
        when(alipay.verifySignature(anyMap())).thenReturn(false);

        var response = api.post("/api/alipay/return-check",
                Map.of("app_id", APP_ID, "out_trade_no", orderNo(orderId), "sign", "x"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }
}
