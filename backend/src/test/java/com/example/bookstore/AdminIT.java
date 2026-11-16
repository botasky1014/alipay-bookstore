package com.example.bookstore;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import com.example.bookstore.alipay.RefundResult;
import com.example.bookstore.support.IntegrationTestBase;

class AdminIT extends IntegrationTestBase {

    @Test
    void stockCanBeIncreasedAndDecreasedButNotBelowZero() {
        assertThat(api.patch("/api/admin/books/1", Map.of("stockDelta", 10)).getBody()).containsEntry("stock", 60);
        assertThat(api.patch("/api/admin/books/1", Map.of("stockDelta", -55)).getBody()).containsEntry("stock", 5);

        var tooMuch = api.patch("/api/admin/books/1", Map.of("stockDelta", -6));
        assertThat(tooMuch.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(stockOf(1)).isEqualTo(5);
    }

    @Test
    void stockHeldByPendingOrdersCannotBeRemoved() {
        createOrder(1, 45);
        assertThat(api.patch("/api/admin/books/1", Map.of("stockDelta", -6)).getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void priceAndTitleChangesAffectOnlyNewOrders() {
        long before = createOrder(1, 1);

        var response = api.patch("/api/admin/books/1", Map.of("price", "12.34", "title", "P and P"));
        assertThat(response.getBody()).containsEntry("title", "P and P");
        assertThat(priceOf(1)).isEqualByComparingTo("12.34");

        long after = createOrder(1, 1);
        assertThat(dec(api.get("/api/orders/" + before).getBody().get("totalAmount"))).isEqualByComparingTo("39.90");
        assertThat(dec(api.get("/api/orders/" + after).getBody().get("totalAmount"))).isEqualByComparingTo("12.34");
    }

    @Test
    void rejectsInvalidEdits() {
        assertThat(api.patch("/api/admin/books/1", Map.of("price", "0")).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(api.patch("/api/admin/books/1", Map.of("price", "1.234")).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(api.patch("/api/admin/books/1", Map.of("title", " ")).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(api.patch("/api/admin/books/1", new HashMap<>()).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(api.patch("/api/admin/books/999", Map.of("stockDelta", 1)).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void fundSummaryTotalsPaymentsAndRefunds() {
        long first = createOrder(1, 1);   // 39.90
        long second = createOrder(2, 2);  // 90.00
        forcePaid(first, START);
        forcePaid(second, START.plusSeconds(60));
        clock.advance(java.time.Duration.ofMinutes(5));
        api.post("/api/orders/" + first + "/refund-requests", Map.of("reason", "x"));
        long requestId = jdbc.queryForObject("SELECT id FROM refund_requests", Long.class);
        when(alipay.refund(anyString(), any(), anyString(), anyString())).thenReturn(RefundResult.success());
        api.post("/api/admin/refund-requests/" + requestId + "/approve", null);
        when(alipay.balanceQuery()).thenReturn(Optional.of(new BigDecimal("1000.00")));

        Map<String, Object> summary = api.get("/api/admin/funds").getBody();

        assertThat(dec(summary.get("totalIn"))).isEqualByComparingTo("129.90");
        assertThat(dec(summary.get("totalOut"))).isEqualByComparingTo("39.90");
        assertThat(dec(summary.get("localNetTotal"))).isEqualByComparingTo("90.00");
        assertThat(dec(summary.get("alipayBalance"))).isEqualByComparingTo("1000.00");
        assertThat(dec(summary.get("difference"))).isEqualByComparingTo("910.00");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> transactions = (List<Map<String, Object>>) summary.get("transactions");
        assertThat(transactions).hasSize(3);
        assertThat(transactions.getFirst()).containsEntry("type", "REFUND_OUT").containsEntry("orderNo", orderNo(first));
    }

    @Test
    void unavailableBalanceIsReportedAsNull() {
        when(alipay.balanceQuery()).thenReturn(Optional.empty());

        Map<String, Object> summary = api.get("/api/admin/funds").getBody();

        assertThat(summary.get("alipayBalance")).isNull();
        assertThat(summary.get("difference")).isNull();
        assertThat(dec(summary.get("localNetTotal"))).isEqualByComparingTo("0");
    }
}
