package com.example.bookstore.cart;

import java.math.BigDecimal;
import java.util.List;

public record CartDto(List<Item> items, BigDecimal totalAmount) {

    /** Price is the live book price; subtotal = price x quantity. */
    public record Item(Long id, Long bookId, String title, String coverImage, BigDecimal price, int quantity,
            int stock, BigDecimal subtotal) {
    }
}
