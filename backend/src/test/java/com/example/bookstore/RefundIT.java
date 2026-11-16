package com.example.bookstore;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import com.example.bookstore.alipay.RefundResult;
import com.example.bookstore.support.IntegrationTestBase;

class RefundIT extends IntegrationTestBase {

    private long paidOrder() {
        long orderId = createOrder(1, 2);
        forcePaid(orderId, START);
        return orderId;
    }

    private long requestRefund(long orderId) {
        var response = api.post("/api/orders/" + orderId + "/refund-requests", Map.of("reason", "Damaged copy"));
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return jdbc.queryForObject("SELECT id FROM refund_requests WHERE order_id = ?", Long.class, orderId);
    }

    private String refundStatus(long requestId) {
        return jdbc.queryForObject("SELECT status FROM refund_requests WHERE id = ?", String.class, requestId);
    }

    @Test
    void onlyPaidOrdersCanRequestARefund() {
        long pending = createOrder(1, 1);
        var response = api.post("/api/orders/" + pending + "/refund-requests", Map.of("reason", "x"));
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void reasonIsRequired() {
        long orderId = paidOrder();
        var response = api.post("/api/orders/" + orderId + "/refund-requests", Map.of("reason", "  "));
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(orderStatus(orderId)).isEqualTo("PAID");
    }

    @Test
    void requestMovesOrderToRefundingAndCannotBeRepeated() {
        long orderId = paidOrder();
        long requestId = requestRefund(orderId);

        assertThat(orderStatus(orderId)).isEqualTo("REFUNDING");
        assertThat(refundStatus(requestId)).isEqualTo("PENDING_REVIEW");
        var again = api.post("/api/orders/" + orderId + "/refund-requests", Map.of("reason", "again"));
        assertThat(again.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);

        Map<String, Object> order = api.get("/api/orders/" + orderId).getBody();
        assertThat(order.get("refund")).asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.MAP)
                .containsEntry("status", "PENDING_REVIEW").containsEntry("reason", "Damaged copy");
    }

    @Test
    void approvalRefundsRestoresStockAndRecordsFundFlow() {
        long orderId = paidOrder();
        long requestId = requestRefund(orderId);
        when(alipay.refund(anyString(), any(), anyString(), anyString())).thenReturn(RefundResult.success());

        var response = api.post("/api/admin/refund-requests/" + requestId + "/approve", null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsEntry("status", "REFUNDED");
        assertThat(orderStatus(orderId)).isEqualTo("REFUNDED");
        assertThat(stockOf(1)).isEqualTo(50);
        assertThat(fundCount("REFUND_OUT")).isEqualTo(1);
        verify(alipay).refund(eq(orderNo(orderId)), any(), eq("RF" + orderNo(orderId)), eq("Damaged copy"));
    }

    @Test
    void failedApprovalKeepsRequestPendingAndRetryUsesSameRequestNumber() {
        long orderId = paidOrder();
        long requestId = requestRefund(orderId);
        when(alipay.refund(anyString(), any(), anyString(), anyString()))
                .thenReturn(RefundResult.failed("40004 Business Failed (ACQ.SELLER_BALANCE_NOT_ENOUGH)"))
                .thenReturn(RefundResult.success());

        var first = api.post("/api/admin/refund-requests/" + requestId + "/approve", null);
        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);
        assertThat(refundStatus(requestId)).isEqualTo("PENDING_REVIEW");
        assertThat(orderStatus(orderId)).isEqualTo("REFUNDING");
        List<Map<String, Object>> list = api.getList("/api/admin/refund-requests").getBody();
        assertThat((String) list.getFirst().get("lastError")).contains("SELLER_BALANCE_NOT_ENOUGH");

        var retry = api.post("/api/admin/refund-requests/" + requestId + "/approve", null);
        assertThat(retry.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(refundStatus(requestId)).isEqualTo("REFUNDED");
        assertThat(api.getList("/api/admin/refund-requests").getBody().getFirst().get("lastError")).isNull();
        verify(alipay, times(2)).refund(anyString(), any(), eq("RF" + orderNo(orderId)), anyString());
    }

    @Test
    void ambiguousRefundResultIsConfirmedWithRefundQuery() {
        long orderId = paidOrder();
        long requestId = requestRefund(orderId);
        when(alipay.refund(anyString(), any(), anyString(), anyString())).thenReturn(RefundResult.unknown("timeout"));
        when(alipay.refundQuery(anyString(), anyString())).thenReturn(RefundResult.success());

        api.post("/api/admin/refund-requests/" + requestId + "/approve", null);

        assertThat(orderStatus(orderId)).isEqualTo("REFUNDED");
    }

    @Test
    void rejectionCompletesTheOrder() {
        long orderId = paidOrder();
        long requestId = requestRefund(orderId);

        var response = api.post("/api/admin/refund-requests/" + requestId + "/reject", null);

        assertThat(response.getBody()).containsEntry("status", "REJECTED");
        assertThat(orderStatus(orderId)).isEqualTo("COMPLETED");
        assertThat(stockOf(1)).isEqualTo(48);
        assertThat(api.post("/api/admin/refund-requests/" + requestId + "/approve", null).getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT);
    }
}
