package com.example.bookstore;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import com.example.bookstore.support.IntegrationTestBase;

class OrderIT extends IntegrationTestBase {

    @Test
    void placingAnOrderDeductsStockAndSnapshotsPrices() {
        var response = api.post("/api/orders", Map.of("userId", BUYER_ID, "fromCart", false, "items",
                List.of(Map.of("bookId", 1, "quantity", 2), Map.of("bookId", 2, "quantity", 1))));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        Map<String, Object> order = response.getBody();
        assertThat(order).containsEntry("status", "PENDING_PAYMENT");
        assertThat((String) order.get("orderNo")).matches("20261116100000\\d{6}");
        // 2 x 39.90 + 1 x 45.00
        assertThat(dec(order.get("totalAmount"))).isEqualByComparingTo("124.80");
        assertThat(stockOf(1)).isEqualTo(48);
        assertThat(stockOf(2)).isEqualTo(49);
    }

    @Test
    void insufficientStockFailsTheWholeOrder() {
        setStock(2, 1);
        var response = api.post("/api/orders", Map.of("userId", BUYER_ID, "fromCart", false, "items",
                List.of(Map.of("bookId", 1, "quantity", 1), Map.of("bookId", 2, "quantity", 3))));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat((String) response.getBody().get("message")).contains("1984");
        assertThat(stockOf(1)).isEqualTo(50);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM orders", Integer.class)).isZero();
    }

    @Test
    void checkoutRemovesOnlyTheOrderedBooksFromTheCart() {
        for (long bookId : new long[] {1, 2, 3}) {
            api.post("/api/cart/items", Map.of("userId", BUYER_ID, "bookId", bookId, "quantity", 1));
        }

        api.post("/api/orders", Map.of("userId", BUYER_ID, "fromCart", true, "items",
                List.of(Map.of("bookId", 1, "quantity", 1), Map.of("bookId", 2, "quantity", 1))));

        assertThat(jdbc.queryForList("SELECT book_id FROM cart_items", Long.class)).containsExactly(3L);
    }

    @Test
    void buyNowLeavesTheCartAlone() {
        api.post("/api/cart/items", Map.of("userId", BUYER_ID, "bookId", 1, "quantity", 1));
        createOrder(1, 1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM cart_items", Integer.class)).isEqualTo(1);
    }

    @Test
    void orderKeepsSnapshotsAfterBookChanges() {
        long orderId = createOrder(1, 1);
        jdbc.update("UPDATE books SET price = 99.00, title = 'Renamed' WHERE id = 1");

        Map<String, Object> order = api.get("/api/orders/" + orderId).getBody();
        @SuppressWarnings("unchecked")
        Map<String, Object> item = ((List<Map<String, Object>>) order.get("items")).getFirst();
        assertThat(item).containsEntry("title", "Pride and Prejudice");
        assertThat(dec(item.get("unitPrice"))).isEqualByComparingTo("39.90");
        assertThat(dec(order.get("totalAmount"))).isEqualByComparingTo("39.90");
    }

    @Test
    void listsOrdersNewestFirst() {
        long first = createOrder(1, 1);
        clock.advance(java.time.Duration.ofMinutes(1));
        long second = createOrder(2, 1);

        var orders = api.getList("/api/orders?userId=" + BUYER_ID).getBody();
        assertThat(orders).extracting(o -> ((Number) o.get("id")).longValue()).containsExactly(second, first);
    }

    @Test
    void rejectsDuplicateBooksAndBadQuantities() {
        var duplicate = api.post("/api/orders", Map.of("userId", BUYER_ID, "fromCart", false, "items",
                List.of(Map.of("bookId", 1, "quantity", 1), Map.of("bookId", 1, "quantity", 1))));
        assertThat(duplicate.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        var zero = api.post("/api/orders", Map.of("userId", BUYER_ID, "fromCart", false, "items",
                List.of(Map.of("bookId", 1, "quantity", 0))));
        assertThat(zero.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }
}
