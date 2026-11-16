package com.example.bookstore;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import com.example.bookstore.support.IntegrationTestBase;

class CartIT extends IntegrationTestBase {

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> items(Map<String, Object> cart) {
        return (List<Map<String, Object>>) cart.get("items");
    }

    private Map<String, Object> add(long bookId, int quantity) {
        return api.post("/api/cart/items", Map.of("userId", BUYER_ID, "bookId", bookId, "quantity", quantity))
                .getBody();
    }

    @Test
    void addingTheSameBookMergesAndCapsAtStock() {
        setStock(1, 5);
        add(1, 2);
        Map<String, Object> cart = add(1, 10);

        assertThat(items(cart)).hasSize(1);
        assertThat(items(cart).getFirst()).containsEntry("quantity", 5);
    }

    @Test
    void cannotAddSoldOutBook() {
        setStock(1, 0);
        var response = api.post("/api/cart/items", Map.of("userId", BUYER_ID, "bookId", 1, "quantity", 1));
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void readingTheCartCapsQuantitiesAndDropsSoldOutBooks() {
        add(1, 4);
        add(2, 3);
        setStock(1, 2);
        setStock(2, 0);

        Map<String, Object> cart = api.get("/api/cart?userId=" + BUYER_ID).getBody();

        assertThat(items(cart)).hasSize(1);
        assertThat(items(cart).getFirst()).containsEntry("bookId", 1).containsEntry("quantity", 2);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM cart_items", Integer.class)).isEqualTo(1);
    }

    @Test
    void showsLivePricesAndTotal() {
        add(1, 2);
        jdbc.update("UPDATE books SET price = 10.50 WHERE id = 1");

        Map<String, Object> cart = api.get("/api/cart?userId=" + BUYER_ID).getBody();

        assertThat(dec(items(cart).getFirst().get("price"))).isEqualByComparingTo("10.50");
        assertThat(dec(cart.get("totalAmount"))).isEqualByComparingTo("21.00");
    }

    @Test
    void updatesAndDeletesItems() {
        Map<String, Object> cart = add(1, 1);
        Object itemId = items(cart).getFirst().get("id");

        cart = api.put("/api/cart/items/" + itemId, Map.of("quantity", 3)).getBody();
        assertThat(items(cart).getFirst()).containsEntry("quantity", 3);

        cart = api.delete("/api/cart/items/" + itemId).getBody();
        assertThat(items(cart)).isEmpty();
    }

    @Test
    void cartSurvivesAcrossRequests() {
        add(3, 1);
        assertThat(items(api.get("/api/cart?userId=" + BUYER_ID).getBody())).hasSize(1);
    }
}
